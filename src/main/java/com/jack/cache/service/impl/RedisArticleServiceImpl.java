package com.jack.cache.service.impl;

import com.jack.cache.basic.Constants;
import com.jack.cache.service.RedisArticleService;
import com.jack.cache.utils.JedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * @ClassName RedisArticleServiceImpl
 * @Description TODO
 * @Author lilei
 * @Date 08/06/2020 20:49
 * @Version 1.0
 **/
@Service
public class RedisArticleServiceImpl implements RedisArticleService {

    @Resource
    private JedisUtils jedis;

    /**
     * 文章提交发布
     * @param title
     * @param content
     * @param link
     * @param userId
     * @return
     */
    @Override
    public String postArticle(String title, String content, String link, String userId) {
        String articleId = String.valueOf(jedis.incr("article:"));
        String voted = "voted:" + articleId;
        jedis.sadd(voted, userId);
        jedis.expire(voted, Constants.ONE_WEEK_IN_SECONDS);

        long now = System.currentTimeMillis() / 1000;

        String article = "article:" + articleId;

        Map<String, String> articleData = new HashMap<>();
        articleData.put("title", title);
        articleData.put("now", String.valueOf(now));
        articleData.put("link", link);
        articleData.put("user", userId);
        articleData.put("votes","1");

        jedis.hmset(articleId, articleData);
        jedis.zadd("score:info", now + Constants.VOTE_SCORE, article);
        jedis.zadd("time:", now, article);
        return articleId;
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        return jedis.hgetAll(key);
    }

    /**
     * 文章投票
     * @param userId
     * @param article
     */
    @Override
    public void articleVote(String userId, String article) {
        // 计算投票截止时间
        long cutoff = (System.currentTimeMillis() / 100) - Constants.ONE_WEEK_IN_SECONDS;
        //检查是否还可以对文章进行投票,如果该文章的发布时间比截止时间小，则已过期，不能进行投票
        if (jedis.zscore("time:", article) < cutoff){
            return;
        }
        //获取文章主键
        String articleId = article.substring(article.indexOf(":") + 1);
        if (jedis.sadd("voted:" + articleId , userId) == 1){
            jedis.zincrby("score:info", Constants.VOTE_SCORE, article); // 分值增加
            jedis.hincrBy(article, "votes", 1L);// 投票数+1
        }
    }

    @Override
    public String hget(String key, String votes) {
        return null;
    }

    /**
     * 文章列表分页查询
     * @param page
     * @param key
     * @return
     */
    @Override
    public List<Map<String, String>> getArticles(int page, String key) {
        int start = (page - 1) * Constants.ARTICLES_PER_PAGE;
        int end = start + Constants.ARTICLES_PER_PAGE - 1;
        //倒序查询出投票数最高的文章，zset有序集合，分值递减
        Set<String> ids = jedis.zrevrange(key, start, end);
        List<Map<String,String>> articles = new ArrayList<Map<String,String>>();
        for (String id : ids){
            Map<String,String> articleData = jedis.hgetAll(id);
            articleData.put("id", id);
            articles.add(articleData);
        }

        return articles;
    }
}
