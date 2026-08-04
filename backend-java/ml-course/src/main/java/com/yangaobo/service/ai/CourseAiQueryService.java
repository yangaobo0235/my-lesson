package com.yangaobo.service.ai;

import com.mybatisflex.core.query.QueryChain;
import com.yangaobo.dto.CategorySimpleListVO;
import com.yangaobo.dto.CoursePageDTO;
import com.yangaobo.dto.ai.AiCursorPage;
import com.yangaobo.dto.ai.CategoryAiDTO;
import com.yangaobo.dto.ai.CourseKnowledgeDTO;
import com.yangaobo.dto.ai.CourseSummaryAiDTO;
import com.yangaobo.entity.Category;
import com.yangaobo.entity.Course;
import com.yangaobo.entity.Episode;
import com.yangaobo.entity.Season;
import com.yangaobo.es.CourseDoc;
import com.yangaobo.mapper.CategoryMapper;
import com.yangaobo.mapper.CourseMapper;
import com.yangaobo.mapper.EpisodeMapper;
import com.yangaobo.mapper.SeasonMapper;
import com.yangaobo.service.CategoryService;
import com.yangaobo.service.CourseService;
import com.yangaobo.vo.PageVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.yangaobo.entity.table.CategoryTableDef.CATEGORY;
import static com.yangaobo.entity.table.CourseTableDef.COURSE;
import static com.yangaobo.entity.table.EpisodeTableDef.EPISODE;
import static com.yangaobo.entity.table.SeasonTableDef.SEASON;

@Service
public class CourseAiQueryService {

    private final CourseService courseService;
    private final CategoryService categoryService;
    private final CourseMapper courseMapper;
    private final CategoryMapper categoryMapper;
    private final SeasonMapper seasonMapper;
    private final EpisodeMapper episodeMapper;

    public CourseAiQueryService(
            CourseService courseService,
            CategoryService categoryService,
            CourseMapper courseMapper,
            CategoryMapper categoryMapper,
            SeasonMapper seasonMapper,
            EpisodeMapper episodeMapper) {
        this.courseService = courseService;
        this.categoryService = categoryService;
        this.courseMapper = courseMapper;
        this.categoryMapper = categoryMapper;
        this.seasonMapper = seasonMapper;
        this.episodeMapper = episodeMapper;
    }

    public CourseKnowledgeDTO getCourse(Long id) {
        Course course = courseService.select(id);
        return toKnowledge(List.of(course)).get(0);
    }

    public List<CourseSummaryAiDTO> search(String keyword, int limit) {
        CoursePageDTO request = new CoursePageDTO();
        request.setPageNum(1);
        request.setPageSize(limit);
        request.setKeyword(keyword);
        PageVO<CourseDoc> page = courseService.search(request);
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(doc -> new CourseSummaryAiDTO(
                        doc.getId(),
                        doc.getTitle(),
                        doc.getAuthor(),
                        doc.getCategoryTitle(),
                        doc.getPrice(),
                        doc.getCover(),
                        doc.getUpdated()))
                .toList();
    }

    public AiCursorPage<CourseKnowledgeDTO> knowledge(Long cursor, int size) {
        List<Course> courses = QueryChain.of(courseMapper)
                .where(COURSE.ID.gt(cursor))
                .orderBy(COURSE.ID.asc())
                .limit(size + 1)
                .list();
        List<CourseKnowledgeDTO> fetched = toKnowledge(courses);
        return AiCursorPage.of(fetched, size, CourseKnowledgeDTO::id);
    }

    public List<CategoryAiDTO> categories() {
        List<CategorySimpleListVO> categories = categoryService.simpleList();
        if (categories == null) {
            return List.of();
        }
        return categories.stream()
                .map(category -> new CategoryAiDTO(category.getId(), category.getTitle()))
                .toList();
    }

    private List<CourseKnowledgeDTO> toKnowledge(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        Map<Long, String> categoryTitles = loadCategoryTitles(courses);
        Map<Long, List<Season>> seasonsByCourse = loadSeasons(courseIds);
        Map<Long, List<Episode>> episodesBySeason = loadEpisodes(seasonsByCourse);

        List<CourseKnowledgeDTO> result = new ArrayList<>(courses.size());
        for (Course course : courses) {
            List<Season> seasons = seasonsByCourse.getOrDefault(course.getId(), List.of());
            List<String> episodeTitles = new ArrayList<>();
            List<String> detailParts = new ArrayList<>();
            LocalDateTime knowledgeUpdated = course.getUpdated();

            for (Season season : seasons) {
                knowledgeUpdated = latest(knowledgeUpdated, season.getUpdated());
                if (hasText(season.getTitle()) || hasText(season.getInfo())) {
                    detailParts.add(joinText(season.getTitle(), season.getInfo()));
                }
                for (Episode episode : episodesBySeason.getOrDefault(season.getId(), List.of())) {
                    knowledgeUpdated = latest(knowledgeUpdated, episode.getUpdated());
                    if (hasText(episode.getTitle())) {
                        episodeTitles.add(episode.getTitle());
                    }
                    if (hasText(episode.getInfo())) {
                        detailParts.add(joinText(episode.getTitle(), episode.getInfo()));
                    }
                }
            }

            result.add(new CourseKnowledgeDTO(
                    course.getId(),
                    course.getTitle(),
                    course.getAuthor(),
                    categoryTitles.get(course.getFkCategoryId()),
                    course.getInfo(),
                    String.join("\n", detailParts),
                    List.copyOf(episodeTitles),
                    knowledgeUpdated));
        }
        return result;
    }

    private Map<Long, String> loadCategoryTitles(List<Course> courses) {
        Map<Long, String> result = new HashMap<>();
        for (Course course : courses) {
            if (course.getCategory() != null) {
                result.put(course.getCategory().getId(), course.getCategory().getTitle());
            }
        }

        List<Long> categoryIds = courses.stream()
                .map(Course::getFkCategoryId)
                .filter(Objects::nonNull)
                .filter(id -> !result.containsKey(id))
                .distinct()
                .toList();
        if (!categoryIds.isEmpty()) {
            QueryChain.of(categoryMapper)
                    .where(CATEGORY.ID.in(categoryIds))
                    .list()
                    .forEach(category -> result.put(category.getId(), category.getTitle()));
        }
        return result;
    }

    private Map<Long, List<Season>> loadSeasons(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return QueryChain.of(seasonMapper)
                .where(SEASON.FK_COURSE_ID.in(courseIds))
                .orderBy(SEASON.FK_COURSE_ID.asc(), SEASON.IDX.asc(), SEASON.ID.asc())
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Season::getFkCourseId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private Map<Long, List<Episode>> loadEpisodes(Map<Long, List<Season>> seasonsByCourse) {
        List<Long> seasonIds = seasonsByCourse.values().stream()
                .flatMap(List::stream)
                .map(Season::getId)
                .toList();
        if (seasonIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return QueryChain.of(episodeMapper)
                .where(EPISODE.FK_SEASON_ID.in(seasonIds))
                .orderBy(EPISODE.FK_SEASON_ID.asc(), EPISODE.IDX.asc(), EPISODE.ID.asc())
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Episode::getFkSeasonId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String joinText(String title, String content) {
        if (!hasText(title)) {
            return content;
        }
        if (!hasText(content)) {
            return title;
        }
        return title + ": " + content;
    }

    private LocalDateTime latest(LocalDateTime current, LocalDateTime candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null || current.isAfter(candidate)) {
            return current;
        }
        return candidate;
    }
}
