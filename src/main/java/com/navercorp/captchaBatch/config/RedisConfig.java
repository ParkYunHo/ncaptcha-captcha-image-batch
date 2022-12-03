package com.navercorp.captchaBatch.config;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.navercorp.captchaBatch.domain.CommonImageInfo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {
	@Value("${" + CommonImageInfo.projectEnvironment +".redis.url}")
	private String redisUrl;											// var : application.yml에서 dev용 Redis url을 가져와 저장하는 변수
	@Value("${" + CommonImageInfo.projectEnvironment +".redis.port}")
	private int redisPort;												// var : application.yml에서 dev용 Redis port를 가져와 저장하는 변수
	
	private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);
	
	/*
	 * Func(Bean) : Jedis Pool을 싱글톤 Bean객체로 만들어서 관리하는 메서드
	 */
	@Bean(name="getJedis")
	public Jedis getJedis() {
		JedisPoolConfig jedisPoolConfig = null;
		JedisPool jedisPool = null;
		Jedis jedis = null;
		try {
			jedisPoolConfig = new JedisPoolConfig();
			jedisPool = new JedisPool(jedisPoolConfig, redisUrl, redisPort);
			jedis = jedisPool.getResource();
			
			// Redis접속이 가능할시에 true값을 넣는 부분
			CommonImageInfo.isAvailableRedis = true;		
		}catch(Exception e) {
			log.error("[getJedis] UserMessage  : Redis 접속불가");
			log.error("[getJedis] SystemMessage: {}", e.getMessage());
			log.error("[getJedis] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			
			// Redis접속이 불가능할시에 false값을 넣는 부분
			CommonImageInfo.isAvailableRedis = false;
		}
		return jedis;
	}
}
