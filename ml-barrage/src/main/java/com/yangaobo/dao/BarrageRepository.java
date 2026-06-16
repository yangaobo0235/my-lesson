package com.yangaobo.dao;

import com.yangaobo.es.BarrageDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @author 杨奥博
 */
public interface BarrageRepository extends ElasticsearchRepository<BarrageDoc, Long> {

}
