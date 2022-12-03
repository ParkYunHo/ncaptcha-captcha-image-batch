package com.navercorp.captchaBatch.tasklet;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.navercorp.captchaBatch.config.RedisConfig;
import com.navercorp.captchaBatch.domain.CommonImageInfo;
import com.navercorp.captchaBatch.service.ImageHandler;
import com.navercorp.captchaBatch.service.ImageListHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

@Component
@StepScope
public class RedisImageProcessingTasklet implements Tasklet {
	@Autowired
	private RedisConfig RedisConfig;
	private Jedis jedis = new Jedis();
	@Autowired
	ImageHandler imageHandler;
	@Autowired
	ImageListHandler imageListHandler;

	Map<String, Map<String, BufferedImage>> answerAllTypePanoramaImagePoolMap = new HashMap<String, Map<String, BufferedImage>>();
	Map<String, Map<String, BufferedImage>> examAllTypePanoramaImagePoolMap = new HashMap<String, Map<String, BufferedImage>>();
	Map<String, Integer> emptyAnswerParnoamaImageMap = new HashMap<String, Integer>();
	Map<String, Integer> emptyExamParnoamaImageMap = new HashMap<String, Integer>();
	
	private static final Logger log = LoggerFactory.getLogger(RedisImageProcessingTasklet.class);

	/*
	 * Func : CaptchaImagePool의 부족한만큼 채우는 메서드를 호출하며 CaptchaImagePool을 만들때 PanoramaImagePool이 하나라도 부족한 경우 Fail을 리턴하여 MySQL에서 PanoramaImage를 가져와
	 *        CaptchaImagePool을 채우는 Step을 실행시키는 함수 
	 */
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		long start = System.currentTimeMillis();
		try {
			// Redis가 shutdown 되어 있는지를 확인하는 부분
			if(CommonImageInfo.isAvailableRedis) {
				jedis = RedisConfig.getJedis();
				
				// 모든 용도(answer,exam)의 각 타입별 panoramaImage를 HashMap에 저장하는 함수를 호출하는 부분
				getAllTypePanoramaImagePool();
				// CaptchaImage를 만드는데 가장 중요한 타입별 정답PanoramaImagePool의 크기가 '0' 이상일때 CaptchaImagePool을 만드는 메서드를 호출한다
				if(answerAllTypePanoramaImagePoolMap.size() > 0) {
					imageListHandler.setResultCaptchaImage(answerAllTypePanoramaImagePoolMap, examAllTypePanoramaImagePoolMap);
				}
				
				// CaptchaImagePool을 만들때 PanoramaImagePool이 하나라도  부족한 전역변수에 부족한 개수를 각각 저장하고 FAIL리턴한다 
				if(emptyAnswerParnoamaImageMap.size() > 0) {
					CommonImageInfo.emptyAnswerParnoamaImageMap = emptyAnswerParnoamaImageMap;
					CommonImageInfo.emptyExamParnoamaImageMap = emptyExamParnoamaImageMap;
					contribution.setExitStatus(ExitStatus.FAILED);
				}
			}else {
				// Redis가 shutdown되어 있을때는 FAIL을 리턴하고 끝낸다
				log.error("[RedisImageProcessing-execute] Redis 접속불가");
				contribution.setExitStatus(ExitStatus.FAILED);
			}
		}catch(Exception e) {
			log.error("[RedisImageProcessing-execute] UserMessage  : Redis에서 부족한 Panorama이미지를 계산하고 가져오는 도중 에러발생");
			log.error("[RedisImageProcessing-execute] SystemMessage: {}", e.getMessage());
			log.error("[RedisImageProcessing-execute] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			contribution.setExitStatus(ExitStatus.FAILED);
		}
		long end = System.currentTimeMillis();
		log.info("runtime={}", (end-start)/1000.0);
		
		return RepeatStatus.FINISHED;
	}
	
	/*
	 * Func : 각각 타입별 정답과 보기 PanoramaImage의 키값과 BufferedImage를 value로 하는 HashMap 전역변수에 값을 저장하는 함수
	 */
	public void getAllTypePanoramaImagePool() throws Exception{
		Random random = new Random(); 	
		String examPanoramaImageKey = "";								// var : 보기PanoramaImage키값을 저장하는 변수
		String answerPanoramaImagePoolKey = "";							// var : 정답PanoramaImagePool의 키값을 저장하는 변수
		String examPanoramaImagePoolKey = "";							// var : 보기PanoramaImagePool의 키값을 저장하는 변수
		List<String> answerPanoramaImagePool = null;					// var : 정답PanoramaImagePool에서 Key값들을 리스트 형식으로 저장하는 변수
		List<String> examRandomPanoramaImageTypeList = null;			// var : 정답으로 사용된 타입을 제외한 타입들을 리스트 형식으로 저장하는 변수 (정답으로 사용된 타입을 제외하고 보기PanoramaImage의 타입을 결정하기 위해 사용)
		Map<String, BufferedImage> answerPanoramaImagePoolMap = null;	// var : 정답PanoramaImage의 키값을 Key로, BufferedImage를 value로 저장하는 HashMap형식의 변수
		Map<String, BufferedImage> examPanoramaImagePoolMap = null;		// var : 보기PanoramaImage의 키값을 Key로, BufferedImage를 value로 저장하는 HashMap형식의 변수
		int emptyCaptchaImagePoolSize = 0;								// var : 최대 captchaImagePool의 크기에 비해 현재 Redis에 저장된 CaptchaImagePool의 부족한 개수를 저장하는 변수
		int emptyAnswerParnoramaImagePoolSize = 0;						// var : 부족한 CaptchaImage의 개수를 만들기에 정답 PanoramaImage의 부족한 개수를 저장하는 변수
		int answerPanoramaImagePoolSize = 0;							// var : 정답PanoramaImagePool의 크기를 저장하는 변수
		int examPanoramaImagePoolSize = 0;								// var : 보기PanoramaImagePool의 크기를 저장하는 변수
		String examPanoramaImageType = "";								// var : 랜덤하게 결정된 보기PanoramaImage의 타입을 저장하는 변수
		String b64 = "";												// var : PanoramaImage의 base64문자열을 저장하는 변수
		byte[] imageByteArray = null;									// var : BufferedImage의 byte 배열을 저장하는 변수
		ByteArrayInputStream bis = null;								// var : base64 String을 디코딩한 byte배열을 읽는 변수
		BufferedImage answerPanoramaImage = null;						// var : 정답PanoramaImage의 BufferedImage를 저장하는 변수	
		BufferedImage examPanoramaImage = null;							// var : 보기PanoramaImage의 BufferedImage를 저장하는 변수
		Pipeline pipeLine = null;
		try {
			// 타입의 개수만큼 반복문을 돌려 각 타입별 정답PanoramaImage와 보기PanoramaImage를 HashMap에 저장하는 부분
			for(String type : CommonImageInfo.typeResizedList) {
				// 최대 captchaImagePool의 크기에 비해 현재 Redis에 저장된 CaptchaImagePool의 부족한 개수를 저장하는 부분
				emptyCaptchaImagePoolSize = CommonImageInfo.maxCaptchaImagePoolSize - jedis.llen(CommonImageInfo.captchaImagePoolKeyHeader + type).intValue();
				// 부족한 개수가 '0'보다 클때에만 프로세스를 진행시킨다
				if(emptyCaptchaImagePoolSize > 0) {
					/* 정답 PanoramaImage에 대한 프로세싱 */
					answerPanoramaImagePoolMap = new HashMap<String, BufferedImage>();
					// 정답PanoramaImagePool의 키값을 저장하는 부분
					answerPanoramaImagePoolKey = CommonImageInfo.answerPanoramaImagePoolKeyHeader + type;
					// 정답PanoramaImagePool의 크기를 저장하는 부분
					answerPanoramaImagePoolSize = jedis.llen(answerPanoramaImagePoolKey).intValue();
					
					// 정답PanoramaImagePool의 크기가 '0'보다 클때에만 프로세스를 진행시킨다
					if(answerPanoramaImagePoolSize > 0){
						// 정답PanoramaImagePool의 전체 키값들을 가져와 리스트 형식의 변수에 저장하는 부분
						answerPanoramaImagePool = jedis.lrange(answerPanoramaImagePoolKey, 0, -1);
						
						if(emptyCaptchaImagePoolSize < answerPanoramaImagePoolSize) {
							// 정답PanoramaImagePool의 개수가 부족한 CaptchaImagePool의 개수보다 클 경우 부족한 개수만큼만 리스트를 짤라서 아래의 반복문을 돌린다
							answerPanoramaImagePool = answerPanoramaImagePool.subList(0, emptyCaptchaImagePoolSize);
						}else {
							// 정답PanoramaImagePool의 개수가 부족한 CaptchaImagePool의 개수보다 작을 경우 CaptchaImagePool을 모두 만들 수 없으므로 그 차이를 별도의 hashMap에 저장한다
							emptyAnswerParnoramaImagePoolSize = emptyCaptchaImagePoolSize - answerPanoramaImagePoolSize;
							emptyAnswerParnoamaImageMap.put(type, emptyAnswerParnoramaImagePoolSize);
						}
						// 정답PanoramaImagePool을 반복문을 돌려 Redis에 저장된 base64를 BufferedImage로 변환하고, panoramaImageKey는 map의 키값으로, BufferedImage는 map의 value로 만들어 저장한다
						for(String answerPanoramaImageKey : answerPanoramaImagePool) {
							// Redis에서 정답PanoramaImageKey가 저장하고 있는 base64정보를 가져오는 부분
							b64 = jedis.hget(answerPanoramaImageKey, CommonImageInfo.base64Field);
							
							// base64를 디코딩하여 BufferedImage로 만드는 부분
							imageByteArray = Base64.getDecoder().decode(b64);
							bis = new ByteArrayInputStream(imageByteArray);
							answerPanoramaImage = ImageIO.read(bis);
							// 정답PanoramaImageKey와 BufferedImage를 한쌍으로 HashMap에 저장된다
							answerPanoramaImagePoolMap.put(answerPanoramaImageKey, answerPanoramaImage);
							
							// captchaImage를 만드는데 사용된 정답PanoramaImage는 바로 삭제한다
							pipeLine = jedis.pipelined();
							pipeLine.lrem(answerPanoramaImagePoolKey, 0, answerPanoramaImageKey);
							pipeLine.del(answerPanoramaImageKey);
							pipeLine.sync();
						}
						// 정답PanoramaImagePool의 부족한 개수만큼의 개수를 저장하고 있는 HashMap을 value로, 타입명을 key로 하는 HashMap에 값을 저장하는 부분
						answerAllTypePanoramaImagePoolMap.put(type, answerPanoramaImagePoolMap);
					}else {
						// 정답 PanoramaImagePool이 아예 비어있는 경우에는 타입명을 key로, 최대CaptchaImagePool의 개수를 value로 하는 HashMap에 저장한다
						// (해당 HashMap의 크기가 '0' 이상인 경우 FAIL을 리턴하고 MySQL에서 PanoramaImage를 Redis로 가져와 CaptchaImagePool을 만드는 Step이 실행된다) 
						emptyAnswerParnoamaImageMap.put(type, CommonImageInfo.maxCaptchaImagePoolSize);
						log.warn("[" + getFullTypeName(type) + "] PanoramaImagePool is empty ");
					} 
					
					/* 보기 PanoramaImage에 대한 프로세싱 */
					// 보기PanoramaImagePool의 키값을 저장하는 부분
					examPanoramaImagePoolKey = CommonImageInfo.answerPanoramaImagePoolKeyHeader + type;
					// 보기PanoramaImagePool의 크기를 저장하는 부분
					examPanoramaImagePoolSize = jedis.llen(examPanoramaImagePoolKey).intValue();
					
					// 보기PanoramaImagePool의 크기가 '0'보다 클때에만 프로세스를 진행시킨다
					if(examPanoramaImagePoolSize > 0) {
						examRandomPanoramaImageTypeList = new ArrayList<String>();
						// 정답이미지로 사용된 타입을 제외하고 나머지 타입들을 저장한 리스트를 만드는 부분
						examRandomPanoramaImageTypeList.addAll(CommonImageInfo.typeResizedList);
						examRandomPanoramaImageTypeList.remove(type);
						// 랜덤한 타입을 사용하기 위해 리스트를 섞는다
						Collections.shuffle(examRandomPanoramaImageTypeList);
						
						examPanoramaImagePoolMap = new HashMap<String, BufferedImage>();
						// 보기PanoramaImage의 개수만큼 반복문을 돌려 Redis에 저장된 base64를 BufferedImage로 변환하고, panoramaImageKey는 map의 키값으로, BufferedImage는 map의 value로 만들어 저장한다
						for(int i=0; i<CommonImageInfo.examPanoramaImageCnt; i++) {
							examPanoramaImageType = examRandomPanoramaImageTypeList.get(i);
							// 보기PanoramaImagePool의 키값을 저장하는 부분
							examPanoramaImagePoolKey = CommonImageInfo.examPanoramaImagePoolKeyHeader + examPanoramaImageType;
							// 보기PanoramaImagePool의 크기를 저장하는 부분
							examPanoramaImagePoolSize = jedis.llen(examPanoramaImagePoolKey).intValue();
							// 보기PanoramaImagePool의 크기가 '0'보다 클때 프로세스 진행
							if(examPanoramaImagePoolSize > 0) {
								// 보기PanoramaImagePool에서 랜덤한 보기PanoramaImage 한개에 대한 키값을 가져오는 부분
								examPanoramaImageKey = jedis.lindex(examPanoramaImagePoolKey, random.nextInt(examPanoramaImagePoolSize));
								
								// base64를 디코딩하여 BufferedImage로 만드는 부분
								b64 = jedis.hget(examPanoramaImageKey, CommonImageInfo.base64Field);
								imageByteArray = Base64.getDecoder().decode(b64);
								bis = new ByteArrayInputStream(imageByteArray);
								examPanoramaImage = ImageIO.read(bis);
								// 보기PanoramaImageKey와 BufferedImage를 한쌍으로 HashMap에 저장된다
								examPanoramaImagePoolMap.put(examPanoramaImageKey, examPanoramaImage);
							}
						}
						// 보기PanoramaImageKey와 BufferedImage를 한쌍으로 저장된 HashMap의 크기가 '0'보다 클때 프로세스를 진행한다
						if(examPanoramaImagePoolMap.size() > 0) {
							examAllTypePanoramaImagePoolMap.put(type, examPanoramaImagePoolMap);
						}
					}else {
						// 보기 PanoramaImagePool이 아예 비어있는 경우에는 타입명을 key로, 최대CaptchaImagePool의 개수를 value로 하는 HashMap에 저장한다
						// (해당 HashMap의 크기가 '0' 이상인 경우 FAIL을 리턴하고 MySQL에서 PanoramaImage를 Redis로 가져와 CaptchaImagePool을 만드는 Step이 실행된다)
						emptyExamParnoamaImageMap.put(type, CommonImageInfo.maxCaptchaImagePoolSize);
					}
				}else {
					log.info("[" + getFullTypeName(type) + "] captchaImagePool is full ");
				}
			}
		}catch(Exception e) {
			log.error("[getAllTypePanoramaImagePool-Redis] UserMessage\t\t: CaptchaImagePool의 부족한 개수를 채우기 위한 충분한 PanoramaImage가 있는지 체크하는 도중 에러발생");
			log.error("[getAllTypePanoramaImagePool-Redis] SystemMessage\t: {}", e.getMessage());
			log.error("[getAllTypePanoramaImagePool-Redis] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
	
	/*
	 * Func : Redis에 저장되는 타입명의 크기를 줄이기 위해 앞의 두개 char만을 사용하는데 PanoramaAPI를 호출하기 위하여 앞의 두개 char를 파라미터로 받아 전체 타입명을 리턴하는 함수
	 */
	public String getFullTypeName(String resizedType) throws Exception{
		int typeIndex = 0;					// var : Reszied한 타입명 리스트에서 찾고자하는 타입의 인덱스를 저장하는 변수
		String typeFullString = "";			// var : Resized하지 않은 전체 타입명을 저장하는 변수
		try {
			typeIndex = CommonImageInfo.typeResizedList.indexOf(resizedType);
			typeFullString = CommonImageInfo.typeList.get(typeIndex); 
		}catch(Exception e) {
			log.error("[getFullTypeName-Redis] UserMessage  : 데이터크기를 줄이기 위해 앞의 두자리만 사용하였던 타입의 전체명을 typeResizedList 리스트에서 찾던 도중 에러발생");
			log.error("[getFullTypeName-Redis] SystemMessage: {}", e.getMessage());
			log.error("[getFullTypeName-Redis] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			typeFullString = "";
		}
		return typeFullString;
	}
}
