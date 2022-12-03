package com.navercorp.captchaBatch.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.navercorp.captchaBatch.config.RedisConfig;
import com.navercorp.captchaBatch.domain.AxisVO;
import com.navercorp.captchaBatch.domain.CommonImageInfo;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

@Service
public class ImageListHandler {
	@Autowired
	private RedisConfig RedisConfig;
	private Jedis jedis = new Jedis();
	@Autowired
	ImageHandler imageHandler;
	
	private static final Logger log = LoggerFactory.getLogger(ImageListHandler.class);

	/*
	 * Func : 멀티쓰레드를 통해 각 타입당 1개의 정답PanoramaImage와 2개의 보기PanoramaImage를  파라미터로 전달하여 CaptchaImage를 만드는 메서드를 호출하는 함수
	 */
	public void setResultCaptchaImage(Map<String, Map<String, BufferedImage>> answerAllPanoramaImagePoolMap, Map<String, Map<String, BufferedImage>> examAllPanoramaImagePoolMap) throws Exception{
		try {
			jedis = RedisConfig.getJedis();
			// ImageHandler Service단에서 영어문자열타입 리스트와 한글문자열타입 리스트를 파라미터로 넘겨 영어문자타입을은 key로, 한글문자타입들은 value로하는 HashMap 전역변수에 값을 저장하는 부분
			imageHandler.setTypeHangulMap(CommonImageInfo.typeResizedList, Arrays.asList(CommonImageInfo.typeHangulArray));
			
			// 멀티쓰레드를 통해 각 타입별로 최대CaptchaImagePool크기만큼 CaptchaImage를 만드는 메서드를 호출하는 부분
			List<CompletableFuture<Void>> completableFutureList = new ArrayList<>();
			for(Entry<String, Map<String, BufferedImage>> entry : answerAllPanoramaImagePoolMap.entrySet()) {
				CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
					try {
						String imageType = entry.getKey();
						setCaptchaImage(entry.getValue(), examAllPanoramaImagePoolMap.get(imageType), imageType);
						return null;
					}catch (Exception e) {return null;}
				});
				completableFutureList.add(future);
			}
			// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
			allFutures.join();
			
			// TestCode
			printCaptchaImagePool();
			//
		}catch(Exception e) {
			log.error("[setResultCaptchaImage] UserMessage  : 타입별로 CaptchaImagePool의 부족한 개수만큼 채우는 setCaptchaImage() 메서드 호출 도중 에러발생");
			log.error("[setResultCaptchaImage] SystemMessage: {}", e.getMessage());
			log.error("[setResultCaptchaImage] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
	
	/*
	 * Func : CaptchaImagePool 크기만큼 CaptchaImagePool을 만드는 메서드를 호출하는 함수
	 */
	public void setCaptchaImage(Map<String, BufferedImage> answerPanoramaImagePoolMap, Map<String, BufferedImage> examPanoramaImagePoolMap, String type) throws Exception{
		try {
			// 멀티쓰레드를 통해 CaptchaImagePool을 만드는 메서드를 호출하는 부분
			List<CompletableFuture<Void>> completableFutureList = new ArrayList<>();
			for(Entry<String, BufferedImage> answerImageEntry : answerPanoramaImagePoolMap.entrySet()) {
				CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
					try {
						// CaptchaImageKey를 만들기 위해 한개의 정답PanoramaImage키와 두개의 보기PanoramaImage의 키를 합쳐서 만드는 값을 저장하는 변수
						String captchaImageKey = "";
						// HashMap에 저장된 보기PanoramaImage들을 리스트형식의 변수로 저장하기 위한 변수
						List<BufferedImage> examPanoramaImageList = new ArrayList<BufferedImage>();
						// HashMap에 저장된 보기PanoramaImage의 키값으로 CaptchaImageKey를 만들고 BufferedImage인 value값을 별도의 리스트에 저장하는 부분 
						for(Entry<String, BufferedImage> examImageEntry : examPanoramaImagePoolMap.entrySet()) {
							captchaImageKey += ( "-" + examImageEntry.getKey() );
							examPanoramaImageList.add(examImageEntry.getValue());
						}
						// 정답PanoramaImage 키값을 붙여 완성된 CaptchaImageKey를 만드는 부분
						captchaImageKey = answerImageEntry.getKey() + captchaImageKey;
						// CaptchaImage의 초기정보들을 Redis에 저장하고, 시작좌표를 리턴받아 변수에 저장하는 부분
						AxisVO answerAxis = setCaptchaImageInitInfo(captchaImageKey, type);
						// 1개의 정답PanoramaImage와 2개의 보기PanoramaImage, 정답좌표, CaptchaImage의 타입(school, bridge..)을 파라미터로 전달하여 CaptchaImage를 리턴받는 부분
						BufferedImage captchaImage = imageHandler.getResultCaptchaImage(answerImageEntry.getValue(), examPanoramaImageList, answerAxis, type);
						// 리턴받은 BufferedImage형식의 CaptchaImage의 Base64로 변환하여 Redis에 저장하는 메서드를 호출하는 부분 
						setCaptchaImageBase64Info(captchaImage, captchaImageKey);
						return null;
					}catch (Exception e) {return null;}
				});
				completableFutureList.add(future);
			}
			// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
			allFutures.join();
		}catch(Exception e) {
			log.error("[setCaptchaImage] UserMessage  : 하나의 타입에 대한 CaptchaImagePool의 부족한 개수만큼  getResultCaptchaImage() 메서드를 멀티쓰레드로 호출하는 도중 에러발생");
			log.error("[setCaptchaImage] SystemMessage: {}", e.getMessage());
			log.error("[setCaptchaImage] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
	
	/*
	 * Func(sync) : 리턴받은 BufferedImage형식의 CaptchaImage를 base64로 변환하여 Redis Server에 저장하는 함수
	 *  			DB에 접속해야하는 부분에서 Thread간의 문맥전환이 일어나면 하나의 thread가 timeout 시간 이상으로 기다릴 수 있으므로 syncronized 키워드를 통해 임계영역으로 만든다
	 */
	synchronized public void setCaptchaImageBase64Info(BufferedImage captchaImage, String captchaImageKey) throws Exception{
		ByteArrayOutputStream output = null;			// var : BufferedImage를 Base64로 변환하기 위해 ByteArray형식의 OutputStream 객체를 저장하는 변수
		byte[] imageByteArray = null;					// var : BufferedImage의 byte 배열을 저장하는 변수
		String b64 = "";								// var : BufferedImage의 Base64문자열을 저장하는 변수
		try {
			output = new ByteArrayOutputStream();
			// PanoramaImage를 ByteArrayOutputStream객체에 쓰는 부분
			ImageIO.write(captchaImage, CommonImageInfo.imageFormatName, output);
			// ByteArrayOutputStream에 쓰인 값을 byte배열 형식으로 변수에 저장하는 부분
			imageByteArray = output.toByteArray();
			output.close();
			// byte배열의 값을 인코딩하여 Base64문자열로 만들고 이를 변수에 저장하는 부분
			b64 = Base64.getEncoder().encodeToString(imageByteArray);
			
			// Redis의 CaptchaImageKey에 base64문자열을 저장하는 부분
			jedis.hset(captchaImageKey, CommonImageInfo.base64Field, b64);
		}catch(Exception e) {
			log.error("[setCaptchaImageBase64Info] UserMessage  : 새로 생성한 CaptchaImage를 Base64 문자열로 변환하여 Redis에 저장하던 도중 에러발생");
			log.error("[setCaptchaImageBase64Info] SystemMessage: {}", e.getMessage());
			log.error("[setCaptchaImageBase64Info] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
	
	/*
	 * Func(sync) : captchaImage의 초기정보를 Redis에 저장하는 부분으로 CaptchaImage를 만드는 메서드에 파라미터로 전달하기위해 시작좌표를 리턴한다
	 *  			DB에 접속해야하는 부분에서 Thread간의 문맥전환이 일어나면 하나의 thread가 timeout 시간 이상으로 기다릴 수 있으므로 syncronized 키워드를 통해 임계영역으로 만든다
	 */
	synchronized public AxisVO setCaptchaImageInitInfo(String captchaImageKey, String type) throws Exception{
		Random random = new Random();
		AxisVO answerAxis = null;
		Pipeline pipeLine = null;
		try {
			answerAxis = new AxisVO();
			answerAxis.setxAxis(random.nextInt((CommonImageInfo.width - CommonImageInfo.imageCellSize)/CommonImageInfo.imageCellSize) * CommonImageInfo.imageCellSize);
			answerAxis.setyAxis(random.nextInt((CommonImageInfo.height - CommonImageInfo.imageCellSize)/CommonImageInfo.imageCellSize) * CommonImageInfo.imageCellSize);
			
			pipeLine = jedis.pipelined();
			pipeLine.rpush(CommonImageInfo.captchaImagePoolKeyHeader + type, captchaImageKey);
			pipeLine.hset(captchaImageKey, CommonImageInfo.captchaImageIssuedCntField, CommonImageInfo.emptyValue);
			pipeLine.hset(captchaImageKey, CommonImageInfo.captchaImageScoreField, CommonImageInfo.emptyValue);
			pipeLine.hset(captchaImageKey, CommonImageInfo.captchaImageHistoryPoolField,  CommonImageInfo.captchaImageHistoryPoolKeyHeader + captchaImageKey);
			pipeLine.hset(captchaImageKey, CommonImageInfo.captchaImageClientPoolField, CommonImageInfo.captchaImageClientPoolKeyHeader + captchaImageKey);
			pipeLine.hset(captchaImageKey, CommonImageInfo.answerXAxisField, String.valueOf(answerAxis.getxAxis()));
			pipeLine.hset(captchaImageKey, CommonImageInfo.answerYAxisField, String.valueOf(answerAxis.getyAxis()));
			pipeLine.sync();
		}catch(Exception e) {
			log.error("[setCaptchaImageInitInfo] UserMessage  : 새로 생성할 CaptchaImage 초기정보를 Redis에 저장하던 도중 에러발생");
			log.error("[setCaptchaImageInitInfo] SystemMessage: {}", e.getMessage());
			log.error("[setCaptchaImageInitInfo] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		return answerAxis;
	}
	
	// Test Code
	public void printCaptchaImagePool() throws Exception{
		// Test Code
		log.info("==============================================");
		log.info("< CaptchaImagePool Size >");
		for(String type : CommonImageInfo.typeList) {
			log.info(type + ": " + jedis.llen(CommonImageInfo.captchaImagePoolKeyHeader + type.substring(0,2)));
		}
		log.info("==============================================");
		// 
	}
}
