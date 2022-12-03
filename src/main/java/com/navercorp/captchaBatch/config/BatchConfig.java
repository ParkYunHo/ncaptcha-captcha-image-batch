package com.navercorp.captchaBatch.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.navercorp.captchaBatch.tasklet.MySQLImageProcessingTasklet;
import com.navercorp.captchaBatch.tasklet.RedisImageProcessingTasklet;

@Configuration
@EnableBatchProcessing
public class BatchConfig {
	@Autowired
	private JobBuilderFactory jobBuilderFactory;
	@Autowired
	private StepBuilderFactory stepBuilderFactory;
	@Autowired
	private RedisImageProcessingTasklet redisImageProcessingTasklet;
	@Autowired
	private MySQLImageProcessingTasklet mySQLImageProcessingTasklet;
	
	// Step : Redis에 저장된 PanoramaImagePool을 가져와 CaptchaImagePool을 생성하는 Step
	public Step redisImageProcessingStep() {
		return stepBuilderFactory.get("redisImageProcessingStep")
				.tasklet(redisImageProcessingTasklet)
				.build();
	}
	
	// Step : Redis에 저장된 PanoramaImagePool의 크기가 CaptchaImagePool을 생성하는데 충분하지 않아, MySQL에서 PanoramaImage를 가져와 Redis에 저장하고 CaptchaImagePool을 생성하는 Step
	public Step mySQLImageProcessingStep() {
		return stepBuilderFactory.get("mySQLImageProcessingStep")
				.tasklet(mySQLImageProcessingTasklet)
				.build();
	}
	
	@Bean
	public Job baseJob() {
		return jobBuilderFactory.get("baseJob")
				.incrementer(new RunIdIncrementer())
				.start(redisImageProcessingStep())
					.on("FAILED")						// Redis에 저장된 PanoramaImagePool의 크기가 CaptchaImagePool을 생성하는데 충분하지 않으면 Fail을 리턴하고 MySQLImageProcessingTasklet을 실행시킨다.		
					.to(mySQLImageProcessingStep())
					.on("*")
					.end()
				.from(redisImageProcessingStep())
					.on("*")
					.end()
				.end()
				.build();
	}
}
