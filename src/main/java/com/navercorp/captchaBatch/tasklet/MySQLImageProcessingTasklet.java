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
import java.util.Map.Entry;

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
import com.navercorp.captchaBatch.domain.ImageInfoVO;
import com.navercorp.captchaBatch.mapper.ImageMapper;
import com.navercorp.captchaBatch.service.ImageHandler;
import com.navercorp.captchaBatch.service.ImageListHandler;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

@Component
@StepScope
public class MySQLImageProcessingTasklet implements Tasklet {
	@Autowired
	private RedisConfig RedisConfig;
	private Jedis jedis = new Jedis();
	@Autowired
	ImageHandler imageHandler;
	@Autowired
	ImageListHandler imageListHandler;
	@Autowired
	ImageMapper imageMapper;
	
	Map<String, Map<String, BufferedImage>> answerAllTypePanoramaImagePoolMap = new HashMap<String, Map<String, BufferedImage>>();
	Map<String, Map<String, BufferedImage>> examAllTypePanoramaImagePoolMap = new HashMap<String, Map<String, BufferedImage>>();
	
	private static final Logger log = LoggerFactory.getLogger(MySQLImageProcessingTasklet.class);

	/*
	 * Func : Redis에 저장된 PanoramaImagePool이 CaptchaImagePool을 생성하기에 너무 적을때 해당 Tasklet이 실행되며 RedisImageProcessingTasklet에서 부족한 PanoramaImage개수를 전역변수에 저장하고
	 *        해당 전역변수를 참조하여 부족한 만큼 MySQL에서 Redis로 PanoramaImage를 가져오고, 가져온 PanoramaImage를 가지고 CaptchaImagePool을 생성하는 함수
	 */
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		long start = System.currentTimeMillis();
		try {
			// Redis가 shutdown 되어 있는지를 확인하는 부분
			if(CommonImageInfo.isAvailableRedis) {
				jedis = RedisConfig.getJedis();
				
				// 이전 Step에서 저장한 정답PanoramaImagePool의 부족한 개수가 '0' 이상일때만 MySQL에서 정답PanoramaImage를 가져와 Redis에 저장
				if(CommonImageInfo.emptyAnswerParnoamaImageMap.size() > 0) {
					setRefillPanoramaImagePool(CommonImageInfo.emptyAnswerParnoamaImageMap, CommonImageInfo.answerString);
				}
				// 이전 Step에서 저장한 보기PanoramaImagePool의 부족한 개수가 '0' 이상일때만 MySQL에서 보기PanoramaImage를 가져와 Redis에 저장
				if(CommonImageInfo.emptyExamParnoamaImageMap.size() > 0) {
					setRefillPanoramaImagePool(CommonImageInfo.emptyExamParnoamaImageMap, CommonImageInfo.examString);
				}
				// 모든 용도(answer,exam)의 각 타입별 panoramaImage를 HashMap에 저장하는 함수를 호출하는 부분
				getAllUsageTypePanoramaImagePool();
				
				// CaptchaImage를 만드는데 가장 중요한 타입별 정답PanoramaImagePool의 크기가 '0' 이상일때 CaptchaImagePool을 만드는 메서드를 호출한다
				if(answerAllTypePanoramaImagePoolMap.size() > 0) {
					imageListHandler.setResultCaptchaImage(answerAllTypePanoramaImagePoolMap, examAllTypePanoramaImagePoolMap);
				}
			}else {
				// Redis가 shutdown되어 있을때는 FAIL을 리턴하고 끝낸다
				log.error("[MySQLImageProcessing-execute] Redis 접속불가");
				contribution.setExitStatus(ExitStatus.FAILED);
			}
		}catch(Exception e) {
			log.error("[MySQLImageProcessing-execute] UserMessage  : MySQL에서 부족한 Panorama이미지를 계산하고 가져오는 도중 에러발생");
			log.error("[MySQLImageProcessing-execute] SystemMessage: {}", e.getMessage());
			log.error("[MySQLImageProcessing-execute] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			contribution.setExitStatus(ExitStatus.FAILED);
		}
		long end = System.currentTimeMillis();
		log.info("runtime={}", (end-start)/1000.0);
		
		return RepeatStatus.FINISHED;
	}
	
	/*
	 * Func : Redis에서 CaptchaImagePool을 생성하기에 부족한 PanoramaImagePool을 MySQL에서 가져와 Redis에서 저장하는 함수
	 */
	public void setRefillPanoramaImagePool(Map<String, Integer> emptyParnoamaImageMap, String purpose) throws Exception{
		List<ImageInfoVO> emptyPanoramaImageList = null;			// var : 파라미터로 전달받은 부족한 PanoramaImage의 개수를 저장하고 있는 HashMap을 ImageInfoVO 리스트로 변환하여 저장하는 변수
		ImageInfoVO emptyPanoramaImage = null;						// var : 부족한 PanoramaImage의 타입과 부족한 개수를 저장하는 ImageInfoVO 객체 변수 
		List<ImageInfoVO> backupPanoramaImagePool = null;			// var : MySQL에서 가져온 panoramaImagePool을 ImageInfoVO 리스트로 저장하는 변수
		try {
			emptyPanoramaImageList = new ArrayList<ImageInfoVO>();
			// 파라미터로 전달받은 부족한 PanoramaImage의 개수와 타입명을 ImageInfoVO 객체에 저장하고, 리스트에 추가하는 부분
			for(Entry<String, Integer> entry : emptyParnoamaImageMap.entrySet()) {
				emptyPanoramaImage = new ImageInfoVO(); 
				emptyPanoramaImage.setTypeName(getFullTypeName(entry.getKey()));
				emptyPanoramaImage.setEmptyCnt(entry.getValue());
				emptyPanoramaImageList.add(emptyPanoramaImage);
			}
			// MySQL에서 부족한 PanoramaImage를 가져와 ImageInfoVO 리스트에 저장하는 부분
			backupPanoramaImagePool = imageMapper.selectImageInfoList(purpose, emptyPanoramaImageList);
			// MySQL에서 가져온 PanoramaImage가 '0'보다 클때 Redis에 저장한다
			if(backupPanoramaImagePool.size() > 0) {
				// 부족한 PanoramaImage개수만 MySQL에서 가져온 row들의 isUsed필드를 '1'로 update하는 부분 (isUsed=1인 row들은 PanoramaImagePool을 만드는 batch실행시 모두 삭제된다) 
				imageMapper.updateImageInfoList(purpose, emptyPanoramaImageList);
				// MySQL에서 가져온 PanoramaImage들을 Redis에 저장하는 메서드를 호출하는 부분
				setRedisPanoramaImagePool(backupPanoramaImagePool, purpose);
				log.info("Refill Backup " + purpose + " Panorama Image from MySQL!");
			}else {
				log.warn("MySQL has no Backup " + purpose + " Panorama Image!");
			}
		}catch(Exception e) {
			log.error("[checkAvailablePanoramaImagePool] UserMessage  : 부족한 PanoramaImagePool 개수만큼 MySQL에서 가져오는 도중 에러발생");
			log.error("[checkAvailablePanoramaImagePool] SystemMessage: {}", e.getMessage());
			log.error("[checkAvailablePanoramaImagePool] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
	
	/*
	 * Func : MySQL에서 가져온 PanoramaImagePool을 Redis에 저장하는 함수
	 */
	public void setRedisPanoramaImagePool(List<ImageInfoVO> backupPanoramaImagePool, String purpose) throws Exception{
		int checkDuplicate = 0;								// var : MySQL에서 가져온 PanoramaImage가 이미 Redis에 저장되어 있는지 확인하고 그 결과값을 저장하는 변수
		int checkUnused = 0;								// var : MySQL에서 가져온 PanoramaImage가 이미 MySQL의 삭제된 이미지들을 저장하는 unused_info Table 저장되어 있는지 확인하고 그 결과값을 저장하는 변수
		String panoramaImagePoolKeyHeader = "";				// var : 파라미터로 전달받은 용도(exam, answer)에 따라 PanoramaImagePool의 Key헤더부분을 저장하는 변수
		String panoramaImagePoolKey = "";					// var : PanoramaImagePool의 키값을 저장하는 변수
		String panoramaImageKey = "";						// var : 하나의 PanoramaImage의 키값을 저장하는 변수
		String panoramaImageResizedKey = "";				// var : Redis에 저장되는 데이터의 크기를 줄이기 위해 bzstNo와 panoTypeCd를 36진수로 변환하여 만든 키값을 저장하는 변수
		String panoramaImageKeyArray[] = new String[2];		// var : panoramaImageKey를 split하여 bzstNo와 panoTypeCd로 나눈 값을 저장하는 문자열배열 변수
		Pipeline pipeLine = null;
		try {
			// 파라미터로 전달받은 용도(exam, answer)에 따라 PanoramaImagePool의 Key헤더부분을 저장하는 부분
			panoramaImagePoolKeyHeader = purpose.contains(CommonImageInfo.answerString) ? CommonImageInfo.answerPanoramaImagePoolKeyHeader : CommonImageInfo.examPanoramaImagePoolKeyHeader;
			// 파라미터로 전달받은 PanoramaImage를 반복문을 통해 모두 Redis에 저장하는 부분
			for(ImageInfoVO backupPanoramaImage : backupPanoramaImagePool) {
				// PanoramaImagePool의 키값을 저장하는 부분 (MySQL에서 타입명은 전체이름을 그대로 사용하므로 substring으로 앞의 두자리만 짤라서 사용해야한다)
				panoramaImagePoolKey = panoramaImagePoolKeyHeader + backupPanoramaImage.getTypeName().substring(0,2);
				// PanoramaImage의 키값을 저장하는 부분
				panoramaImageKey = backupPanoramaImage.getImageKey();
				// panoramaImageKey를 split하여 bzstNo와 panoTypeCd로 나눈 값을 저장하는 부분
				panoramaImageKeyArray = panoramaImageKey.split("_"); 
				// Redis에 저장되는 데이터의 크기를 줄이기 위해 bzstNo와 panoTypeCd를 36진수로 변환하여 만든 키값을 변수에 저장하는 부분
				panoramaImageResizedKey = Long.toString(Integer.parseInt(panoramaImageKeyArray[0]), 36) + "_" + Long.toString(Integer.parseInt(panoramaImageKeyArray[1]), 36);
				
				// panoramaImageKey가 Redis에 중복되는지를 확인하는 부분
				checkDuplicate = jedis.lrem(panoramaImagePoolKey, 0, panoramaImageResizedKey).intValue();
				// panoramaImageKey가 MySQL의 삭제된 이미지들을 저장하는 unused_info Table에 저장되어 있는지를 확인하는 부분
				checkUnused = imageMapper.selectCheckUnusedImage(panoramaImageKey);
				if(checkDuplicate > 0) {
					log.warn("[MySQL-setPanoramaImagePool] Duplicated PanoramaImage\t: "+ panoramaImageKey);
					jedis.lpush(panoramaImagePoolKey, panoramaImageResizedKey);
				}else if(checkUnused > 0){
					log.warn("[MySQL-setPanoramaImagePool] Deleted PanoramaImage\t: "+ panoramaImageKey);
				}else {
					// 정상적인 PanoramaImage라면 이미지에 대한 정보들을 Redis에 저장한다
					pipeLine = jedis.pipelined();
					pipeLine.lpush(panoramaImagePoolKey, panoramaImageResizedKey);
					pipeLine.hset(panoramaImageResizedKey, CommonImageInfo.base64Field, backupPanoramaImage.getB64());
					pipeLine.sync();
				}
			}
		}catch(Exception e) {
			log.error("[setPanoramaImagePool] UserMessage  : MySQL에서 가져온 PanoramaImagePool의 유효성검사와 Redis에 해당 PanoramaImagePool을 저장하는 도중 에러발생");
			log.error("[setPanoramaImagePool] SystemMessage: {}", e.getMessage());
			log.error("[setPanoramaImagePool] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
	
	/*
	 * Func : 각각 타입별 정답과 보기 PanoramaImage의 키값과 BufferedImage를 value로 하는 HashMap 전역변수에 값을 저장하는 함수   
	 */
	public void getAllUsageTypePanoramaImagePool() throws Exception{
		Random random = new Random(); 	
		String examPanoramaImageKey = "";								// var : 보기PanoramaImage키값을 저장하는 변수
		String answerPanoramaImagePoolKey = "";							// var : 정답PanoramaImagePool의 키값을 저장하는 변수
		String examPanoramaImagePoolKey = "";							// var : 보기PanoramaImagePool의 키값을 저장하는 변수
		List<String> answerPanoramaImagePool = null;					// var : 정답PanoramaImagePool에서 Key값들을 리스트 형식으로 저장하는 변수
		List<String> examRandomPanoramaImageTypeList = null;			// var : 정답으로 사용된 타입을 제외한 타입들을 리스트 형식으로 저장하는 변수 (정답으로 사용된 타입을 제외하고 보기PanoramaImage의 타입을 결정하기 위해 사용)
		Map<String, BufferedImage> answerPanoramaImagePoolMap = null;	// var : 정답PanoramaImage의 키값을 Key로, BufferedImage를 value로 저장하는 HashMap형식의 변수
		Map<String, BufferedImage> examPanoramaImagePoolMap = null;		// var : 보기PanoramaImage의 키값을 Key로, BufferedImage를 value로 저장하는 HashMap형식의 변수
		int emptyCaptchaImagePoolSize = 0;								// var : 최대 captchaImagePool의 크기에 비해 현재 Redis에 저장된 CaptchaImagePool의 부족한 개수를 저장하는 변수
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
					// 정답PanoramaImagePool에서 Key값들을 리스트 형식으로 저장하는 부분
					answerPanoramaImagePool = jedis.lrange(answerPanoramaImagePoolKey, 0, -1);
					// 정답PanoramaImagePool의 Key값들을 저장하고 있는 리스트의 크기를 저장하는 부분
					answerPanoramaImagePoolSize = answerPanoramaImagePool.size();
					
					// 리스트의 크기가 '0'보다 클때에만 프로세스를 진행시킨다
					if(answerPanoramaImagePoolSize > 0){
						// 정답PanoramaImagePool의 개수가 부족한 CaptchaImagePool의 개수보다 클 경우 부족한 개수만큼만 리스트를 짤라서 아래의 반복문을 돌린다
						if(emptyCaptchaImagePoolSize < answerPanoramaImagePoolSize) {
							answerPanoramaImagePool = answerPanoramaImagePool.subList(0, emptyCaptchaImagePoolSize);
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
						log.warn("[" + getFullTypeName(type) + "] PanoramaImagePool is empty ");
					} 
					
					/* 보기 PanoramaImage에 대한 프로세싱 */
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
					log.info("[" + getFullTypeName(type) + "] captchaImagePool is full ");
				}
			}
		}catch(Exception e) {
			log.error("[getAllTypePanoramaImagePool-MySQL] UserMessage  : CaptchaImagePool의 부족한 개수를 채우기 위한 충분한 PanoramaImage가 있는지 체크하는 도중 에러발생");
			log.error("[getAllTypePanoramaImagePool-MySQL] SystemMessage: {}", e.getMessage());
			log.error("[getAllTypePanoramaImagePool-MySQL] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
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
			log.error("[getFullTypeName-MySQL] UserMessage  : 데이터크기를 줄이기 위해 앞의 두자리만 사용하였던 타입의 전체명을 typeResizedList 리스트에서 찾던 도중 에러발생");
			log.error("[getFullTypeName-MySQL] SystemMessage: {}", e.getMessage());
			log.error("[getFullTypeName-MySQL] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			typeFullString = "";
		}
		return typeFullString;
	}
}
