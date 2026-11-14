package com.yangaobo.servicedesk.ticket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证默认组件装配配置、原生本机导入及生产隔离，不建立外部连接。
 *
 * @author yangaobo
 */
class LocalConfigurationTest {

    /**
     * 解析正式配置和空模板，检查默认组件开关及项目端口。
     * @throws IOException 配置读取失败
     */
    @Test
    void parsesProfilesAndKeepsTemplateCredentialsEmpty() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String filename : List.of("application.yml", "application-dev.yml", "application-prod.yml")) {
            assertThat(loader.load(filename, new ClassPathResource(filename))).isNotEmpty();
        }
        Path template = Path.of("src/main/resources/application-local.example.yml");
        PropertySource<?> source = loader.load("local-template", new FileSystemResource(template)).getFirst();
        assertThat(source.getProperty("DESK_COMPONENT_HOST")).isEqualTo("192.168.23.66");
        assertThat(source.getProperty("DESK_NACOS_PASSWORD")).isEqualTo("");
        assertThat(source.getProperty("DESK_AI_MODEL__API_KEY")).isNull();
        PropertySource<?> common = loader.load("common", new ClassPathResource("application.yml")).getFirst();
        assertThat(common.getProperty("desk.infrastructure.enabled")).isEqualTo(true);
        assertThat(common.getProperty("spring.cloud.nacos.discovery.enabled")).isEqualTo(true);
        assertThat(common.getProperty("spring.cloud.sentinel.enabled")).isEqualTo(true);
        assertThat(common.getProperty("server.port")).isEqualTo(26103);
        assertThat(common.getProperty("spring.autoconfigure.exclude")).isNull();
        assertThat(common.getProperty("management.zipkin.tracing.endpoint"))
                .isEqualTo("http://${DESK_COMPONENT_HOST:192.168.23.66}:9411/api/v2/spans");
        assertThat(new ClassPathResource("application-connected.yml").exists()).isFalse();
    }

    /**
     * 不选功能Profile即原生导入本机参数，开发配置启用真实组件且允许系统属性覆盖。
     * @param directory 隔离配置目录
     * @throws IOException 配置复制失败
     */
    @Test
    void defaultDevelopmentLoadsComponentsAndNativeLocalYaml(@TempDir Path directory) throws IOException {
        prepareConfiguration(directory);
        runner(directory).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getDefaultProfiles()).containsExactly("dev");
            assertThat(environment.getProperty("DESK_NACOS_PASSWORD")).isEqualTo("p#$ (literal)");
            assertThat(environment.getProperty("DESK_TEST_REMOTE_LOADED")).isEqualTo("true");
            assertThat(environment.getProperty("desk.infrastructure.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("desk.infrastructure.resource-env")).isEqualTo("test");
        });
        runner(directory).withSystemProperties("DESK_NACOS_PASSWORD=process-value").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("DESK_NACOS_PASSWORD")).isEqualTo("process-value");
        });
    }

    /**
     * 生产使用prod资源且不导入旁边的开发密码文件；仅替换配置中心位置，不启动SDK。
     * @param directory 隔离配置目录
     * @throws IOException 配置复制失败
     */
    @Test
    void productionNeverImportsDevelopmentSecrets(@TempDir Path directory) throws IOException {
        prepareConfiguration(directory);
        runner(directory).withPropertyValues("spring.profiles.active=prod",
                "DESK_TEST_REMOTE_LOCATION=" + directory.resolve("nacos-stub.yml").toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("DESK_NACOS_PASSWORD")).isNull();
            assertThat(environment.getProperty("desk.infrastructure.resource-env")).isEqualTo("prod");
            assertThat(environment.getProperty("DESK_TEST_REMOTE_LOADED")).isEqualTo("true");
            assertThat(environment.getProperty("desk.infrastructure.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("spring.datasource.url")).contains("/desk_ticket_prod?");
            assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("desk_ticket_prod");
            assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        });
    }

    /**
     * 缺失本机文件时明确失败，避免无意回到不连接组件的骨架启动。
     * @param directory 隔离配置目录
     * @throws IOException 配置复制失败
     */
    @Test
    void developmentRequiresLocalConfiguration(@TempDir Path directory) throws IOException {
        prepareConfiguration(directory);
        Files.delete(directory.resolve("application-local.yml"));
        runner(directory).run(context -> assertThat(context).hasFailed());
    }

    /**
     * 复制真实配置，仅将本机和Nacos导入位置替换为有界临时文件。
     * @param directory 当前用例独占目录
     * @throws IOException 配置复制失败
     */
    private void prepareConfiguration(Path directory) throws IOException {
        Path local = directory.resolve("application-local.yml");
        Path remote = directory.resolve("nacos-stub.yml");
        String localTemplate = Files.readString(Path.of("src/main/resources/application-local.example.yml"));
        Files.writeString(local, localTemplate.replace("DESK_NACOS_PASSWORD: \"\"", "DESK_NACOS_PASSWORD: 'p#$ (literal)'")
                .replace("nacos:desk-ticket.yaml?group=${DESK_NACOS_GROUP:DESK_GROUP}&refreshEnabled=true", "${DESK_TEST_REMOTE_LOCATION}")
                + "\nDESK_TEST_REMOTE_LOCATION: '" + remote.toUri() + "'\n");
        Files.writeString(remote, "DESK_TEST_REMOTE_LOADED: true\n");
        for (String filename : List.of("application.yml", "application-dev.yml", "application-prod.yml")) {
            String text;
            try (var input = new ClassPathResource(filename).getInputStream()) {
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            text = text.replace("file:./desk-ticket/src/main/resources/application-local.yml", local.toUri().toString())
                    .replace("nacos:desk-ticket.yaml?group=${DESK_NACOS_GROUP:DESK_GROUP}&refreshEnabled=true",
                            "${DESK_TEST_REMOTE_LOCATION}");
            Files.writeString(directory.resolve(filename), text);
        }
    }

    /**
     * 只启动ConfigData属性加载，不扫描应用Bean，不读取真实开发目录。
     * @param directory 隔离配置目录
     * @return 原生配置加载运行器
     */
    private ApplicationContextRunner runner(Path directory) {
        return new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + directory.toUri());
    }
}
