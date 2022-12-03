package com.navercorp.captchaBatch.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.navercorp.captchaBatch.domain.AxisVO;
import com.navercorp.captchaBatch.domain.CommonImageInfo;

@Service
public class ImageHandler {
	private Map<String, String> typeHangulMap = null;
	
	private static final Logger log = LoggerFactory.getLogger(ImageHandler.class);

	/*
	 * Func : CaptchaImage와 문제텍스트 이미지를 결합한 최종 CaptchaImage를 리턴하는 함수
	 */
	public BufferedImage getResultCaptchaImage(BufferedImage answerPanoramaImage, List<BufferedImage> examPanoramaImageList, AxisVO answerAxis, String answerType) throws Exception{
		BufferedImage resultCaptchaImage = new BufferedImage(CommonImageInfo.width, CommonImageInfo.height, BufferedImage.TYPE_INT_RGB);	// var : 최종 CaptchaImage를 저장하기위한 BufferedImage 변수
		// CaptchaImage를 최종CaptchaImage에 저장하는 부분
		resultCaptchaImage = getCaptchaImage(answerPanoramaImage, examPanoramaImageList, answerAxis);
		// CaptchaImage와 문제텍스트 이미지를 결합한 captchaImage를 최종 CaptchaImage에 저장하는 부분 
		resultCaptchaImage = getDescriptImage(resultCaptchaImage, answerType);
		return resultCaptchaImage;
	}
	
	/*
	 * Func : 한개의 정답이미지와 두개의 보기이미지로 CaptchaImage를 만드는 함수
	 */
	public BufferedImage getCaptchaImage(BufferedImage answerPanoramaImage, List<BufferedImage> examPanoramaImageList, AxisVO answerAxis) throws Exception{
		BufferedImage captchaImage = null;									// var : captchaImage를 저장하기 위한 BufferedImage 변수
		List<BufferedImage> examResizedPanoramaImageList = null;			// var : 랜덤한 보기이미지를 랜덤하게 짤라낸 이미지들의 리스트를 저장하는 변수 
		List<BufferedImage> availableExamPanoramaImageList = null;			// var : 사용가능한 보기PanoramaImage들을 저장하고 있는 리스트 변수
		Graphics2D g = null;												// var : CaptchaImage를 만들기 위한 그래픽 라이브러리 변시
		int availableExamPanoramaImageListIndex = 0;						// var : 사용가능한 보기PanoramaImage리스트에서 인덱스값을 저장하는 변수
		int answerXAxis = 0, answerYAxis = 0;								// var : CaptchaImage의 정답좌표의 x축과 y축을 저장하는 변수
		try {
			captchaImage = new BufferedImage(CommonImageInfo.width, CommonImageInfo.height, BufferedImage.TYPE_INT_RGB);
			examResizedPanoramaImageList = new ArrayList<BufferedImage>();
			availableExamPanoramaImageList = new ArrayList<BufferedImage>();
			g = captchaImage.createGraphics();
			
			// 파라미터로 전달받은 정답좌표의 x축과 y축값을 변수에 저장하는 부분
			answerXAxis = answerAxis.getxAxis();
			answerYAxis = answerAxis.getyAxis();
			
			// 300x300 보기PanoramaImage들을 프로세싱하여 100x100의 보기PanoramaImageList를 변수에 저장하는 부분
			examResizedPanoramaImageList = getResizedExamPanoramaImageList(examPanoramaImageList);
			// 각각의 보기 PanoramaImage들을 반복문을 통해 사용가능한 보기PanoramaImage인지를 체크하고 사용가능한 이미지일때에만 사용가능한 보기PanoramaImageList에 저장한다 
			for(BufferedImage examResizedPanoramaImage : examResizedPanoramaImageList) {
				if(checkAvailableExamPanoramaImage(examResizedPanoramaImage)) {
					availableExamPanoramaImageList.add(examResizedPanoramaImage);
				}
			}
			// 랜덤한 보기PanoramaImage를 사용하기 위해 List를 섞는 부분
			Collections.shuffle(availableExamPanoramaImageList);
			
			// 파라미터로 전달받은 정답좌표에 정답PanoramaImage를 삽입하고 나머지 부분에는 사용가능한 보기PanoramaImage를 삽입하여 하나의 완성된 CaptchaImage를 만드는 부분  
			for(int w=0; w < CommonImageInfo.width; w+=CommonImageInfo.imageCellSize) {
				for(int h=0; h < CommonImageInfo.height; h+=CommonImageInfo.imageCellSize) {
					if(w == answerXAxis && h == answerYAxis) {
						// 파라미터로 전달받은 정답좌표의 경우, 정답PanoramaImage를 삽입하는 부분
						g.drawImage(answerPanoramaImage, w, h, null);
					}else {
						// 정답좌표가 아닌 위치에는 사용가능한 보기PanoramaImage를 삽입하는 부분
						g.drawImage(availableExamPanoramaImageList.get(availableExamPanoramaImageListIndex++), w, h, null);
					}
				}
			}
		}catch(Exception e) {
			log.error("[getCaptchaImage] UserMessage  : 정답이미지와 보기이미지를 합쳐 캡차이미지를 만드는 도중 에러발생");
			log.error("[getCaptchaImage] SystemMessage: {}", e.getMessage());
			log.error("[getCaptchaImage] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		// Graphics2D 라이브러리의 메모리를 반환하는 부분
		g.dispose();
		
		return captchaImage;
	}
	
	/*
	 * Func : 보기 PanoramaImage를 300x300에서 150x150으로 짜르고 이를 다시 300x300으로 사이즈를 변경한 다음, 100x100의 사이즈로 다시 짤라낸 보기PanoramaImage 리스트를 리턴하는 함수
	 */
	public List<BufferedImage> getResizedExamPanoramaImageList(List<BufferedImage> examPanoramaImageList) throws Exception{
		Random random = new Random();
		int halfWidth = 0, halfHeight = 0;										// var : 보기PanoramaImage를 300x300에서 150x150으로 짤라내기 위한 가로, 세로값을 저장하는 변수
		BufferedImage examOriginPanoramaImage = null;							// var : 사이즈를 변경하지 않은 본래 사이즈(300x300)의 보기PanoramaImage를 저장하는 변수 
		BufferedImage examResizedPanoramaImage = null;							// var : 랜덤한 보기이미지를 랜덤하게 짤라낸 이미지를 저장하는 변수
		Graphics2D g = null;													// var : CaptchaImage를 만들기 위한 그래픽 라이브러리 변수
		int examPanoramaImageListIndex = 0;										// var : 파라미터로 전달받은 보기PanoramaImageList를 순서대로 사용하기 위한 인덱스값을 저장하는 변수
		int examPanoramaImageListSize = 0;										// var : 파라미터로 전달받은 보기PanoramaImageList의 크기를 저장하는 변수
		List<BufferedImage> examResizedPanoramaImageList = null;				// var : 랜덤한 보기이미지를 랜덤하게 짤라낸 이미지들의 리스트를 저장하는 변수
		List<BufferedImage> examSplitedPanoramaImageList = null;				// var : 300x300 보기PanoramaImage를 100x100으로 짜른 보기PanoramaImage 리스트를 저장하는 변수
		try {
			examResizedPanoramaImageList = new ArrayList<BufferedImage>();
			examSplitedPanoramaImageList = new ArrayList<BufferedImage>();
			// 보기PanoramaImage로 사용되는 두개의 PanoramaImage를 겹치게 사용되지 않도록 {0,1,0,1} 또는 {1,0,1,0} 방식 중에서 랜덤하게 사용
			examPanoramaImageListSize = examPanoramaImageList.size();
			examPanoramaImageListIndex = random.nextInt(examPanoramaImageListSize);
			// 보기PanoramaImage를 300x300에서 150x150으로 짤라내기 위한 가로, 세로값을 저장하는 변수
			halfWidth = CommonImageInfo.width/2;
			halfHeight = CommonImageInfo.height/2;
			
			// 300x300 보기PanoramaImage들을 저장하는 리스트에서 랜덤한 보기PanoramaImage를 가져와 150x150으로 짤라내고, 짤라낸 150x150 보기PanoramaImage를 다시 300x300으로 사이즈를 키우는 부분
			// 보기PanoramaImage의 좀더 일부분을 사용하여 CaptchaImage의 보기이미지로 사용하기 위해 해당 방식 사용
			for(int w=0; w<CommonImageInfo.width; w+=halfWidth) {
				for(int h=0; h<CommonImageInfo.height; h+=halfHeight) {
					// 파라미터로 전달받은 보기PanoramaImageList를 {0,1,0,1} 또는 {1,0,1,0} 방식 중에서 랜덤하게 사용하기 위하여 현재 인덱스가 '1'일 경우에는 '0'으로, '0'일 경우에는 '1'로 바꿔주는 부분
					examPanoramaImageListIndex = examPanoramaImageListIndex == examPanoramaImageListSize-1 ? 0 : 1;
					// 보기PanoramaImage를 순서대로 각각 (0,0,150,150), (150,0,300,150), (0,150, 150,300), (150,150,300,300) 만큼 짤라내는 부분
					examOriginPanoramaImage = examPanoramaImageList.get(examPanoramaImageListIndex).getSubimage(w, h, halfWidth, halfHeight);
					// 150x150으로 짤린 보기PanoramaImage를 300x300으로 사이즈를 변경하는 부분
					examResizedPanoramaImage = new BufferedImage(CommonImageInfo.width, CommonImageInfo.height, BufferedImage.TYPE_INT_RGB);
					g = examResizedPanoramaImage.createGraphics();
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g.drawImage(examOriginPanoramaImage, 0, 0, CommonImageInfo.width, CommonImageInfo.height, 0, 0, examOriginPanoramaImage.getWidth(), examOriginPanoramaImage.getHeight(), null);
					
					// 300x300으로 변경된 보기PanoramaImage를 리스트에 저장하는 부분
					examResizedPanoramaImageList.add(examResizedPanoramaImage);
				}
			}
			// 300x300의 보기 PanoramaImage를 100x100으로 짤라 리스트에 저장하는 부분 
			for(BufferedImage examPanoramaImage : examResizedPanoramaImageList) {
				for(int w=0; w < CommonImageInfo.width; w+=CommonImageInfo.imageCellSize) {
					for(int h=0; h < CommonImageInfo.height; h+=CommonImageInfo.imageCellSize) {
						examSplitedPanoramaImageList.add(examPanoramaImage.getSubimage(w, h, CommonImageInfo.imageCellSize, CommonImageInfo.imageCellSize));
					}
				}
			}
		}catch(Exception e) {
			log.error("[getResizedExamPanoramaImageList] UserMessage  : 정답이미지와 보기이미지를 합쳐 캡차이미지를 만드는 도중 에러발생");
			log.error("[getResizedExamPanoramaImageList] SystemMessage: {}", e.getMessage());
			log.error("[getResizedExamPanoramaImageList] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		return examSplitedPanoramaImageList;
	}
	
	/*
	 * Func : 보기PanoramaImage의 RGB평균값이 특정 범위 이내인지, Blue평균값이 너무 높진 않은지, 표준편차가 너무 적은 이미지는 아닌지를 판단하여 사용가능한 보기PanoramaImage만을 리턴하는 함수
	 */
	public boolean checkAvailableExamPanoramaImage(BufferedImage examPanoramaImage) throws Exception{
		List<Double> pixelAverageValueList = null;											// var : 보기PanoramaImage의 각 픽셀들의 평균값을 리스트형식으로 저장하는 변수
		double sumValue = 0.0, standardDeviationValue = 0.0, differentValue = 0.0;			// var : RGB평균들의 합계값, 표준편차, 각 픽셀의 평균과 전체평균간의 차이값을 저장하는 변수
		double totalAverageValue = 0.0, pixelAverageValue = 0.0, blueAverageValue = 0.0;	// var : 전체 RGB평균, 하나의 픽셀의 RGB평균, Blue값에 대한 RGB평균을 저장하는 변수
		int redValue = 0, greenValue = 0, blueValue = 0;									// var : 각 픽셀들의 red, green, blue값을 저장하는 변수
		int blueSumValue = 0;																// var : 모든 픽셀의 blue값의 합계를 저장하는 변수
		int pixelAverageValueListSize = 0;													// var : 보기PanoramaImage의 각 픽셀들의 평균값들을 저장하는 리스트의 크기를 저장하는 변수
		try {
			pixelAverageValueList = new ArrayList<Double>();
			// 반복문을 통해 보기PanoramaImage의 하나하나의 픽셀의 평균값과 blue의 합계를 계산하는 부분 
			for (int w = 0; w < CommonImageInfo.imageCellSize; w++) {
				for (int h = 0; h < CommonImageInfo.imageCellSize; h++) {
					redValue = new Color(examPanoramaImage.getRGB(w, h)).getRed();
					greenValue = new Color(examPanoramaImage.getRGB(w, h)).getGreen();
					blueValue = new Color(examPanoramaImage.getRGB(w, h)).getBlue();

					// 보기이미지 중에서 하늘이 많이 포함된 이미지를 제거하기 위해 blue값의 합계를 구하는 부분
					blueSumValue += blueValue; 	
					// 각 픽셀의 평균RGB값을 구하고 리스트에 저장하는 부분
					pixelAverageValue = (redValue + greenValue + blueValue) / 3.0;
					pixelAverageValueList.add(pixelAverageValue);
				}
			}

			// 각 픽셀들의 RGB평균값들에 대한 평균값을 계산하기 위하여 각각의 평균값들을 더하는 부분
			for(Double averageItemValue : pixelAverageValueList) {
				sumValue += averageItemValue;
			}
			// 각 픽셀들의 RGB평균값들에 대한 평균값을 계산하기 위하여 각각의 평균값들의 개수를 계산하는 부분
			pixelAverageValueListSize = pixelAverageValueList.size();
			// 각 픽셀들의 RGB평균값들에 대한 평균값을 계산하는 부분
			totalAverageValue = (double)sumValue / pixelAverageValueListSize;
			// 각 픽셀들의 blue 평균값을 계산하는 부분 (Blue평균값이 너무 높으면 하늘만 보이는 보기이미지이므로 이를 보기이미지에서 제외하기 위하여 계산)
			blueAverageValue = (double)blueSumValue / pixelAverageValueListSize;		
			
			// 각 픽셀들의 RGB평균값들에 대한 평균값이 최소값보다 크거나 (너무 어두운 보기이미지를 제외하기 위해서), 최대값보다 작은 (너무 밝은 보기이미지는 제외하기 위해서)지를 확인하는 부분
			if(CommonImageInfo.minTotalAverageValue < totalAverageValue && totalAverageValue < CommonImageInfo.maxTotalAverageValue) {
				// blue 평균값이 최대 blue평균값보다 작은지 확인하는 부분 (Blue평균값이 너무 높으면 하늘만 보이는 보기이미지이므로 이를 보기이미지에서 제외하기 위하여 계산)
				if(blueAverageValue < CommonImageInfo.maxBlueAverageValue) {
					// 표준편차 알고리즘에 의해 각각의 픽셀들의 표준편차를 계산하는 부분
					for(Double averageItemValue : pixelAverageValueList) {
						differentValue = averageItemValue - totalAverageValue;
						sumValue += differentValue * differentValue;
					}
					standardDeviationValue = Math.sqrt(sumValue / pixelAverageValueListSize);
					// 표준편차가 최대 표준편차크기보다 커야지만 true를 리턴한다 (표준편차가 너무 작으면 하나의 무늬만 보이는 보기이미지이므로 어뷰저가 판단하기 쉬워 제외)
					if(standardDeviationValue >= CommonImageInfo.maxStandardDeviationValue) {
						return true;
					}
				}
			}
		}catch(Exception e) {
			log.error("[checkAvailableExamPanoramaImage] UserMessage  : 보기이미지의 표준편차를 구하는 도중 에러발생");
			log.error("[checkAvailableExamPanoramaImage] SystemMessage: {}", e.getMessage());
			log.error("[checkAvailableExamPanoramaImage] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		return false;
	}
	
	/*
	 * Func : 문제텍스트 이미지를 프로세싱하여 리턴하는 함수
	 */
	public BufferedImage getDescriptImage(BufferedImage captchaImage, String answerType) throws Exception{
		BufferedImage descriptImage = null;			// var : 문제텍스트 이미지를 저장하는 변수
		BufferedImage resultCaptchaImage = null;	// var : 문제텍스트 이미지와 캡차이미지를 합친 최종 캡차이미지를 저장하는 변수
		Graphics2D g = null;						// var : CaptchaImage를 만들기 위한 그래픽 라이브러리 변수
		String answerTypeHangulString = "";			// var : 정답PanoramaImage타입에 대한 한글문자열을 저장하는 변수
		String resultDescriptString = "";			// var : 문제텍스트의 문자열을 저장하는 변수
		try {
			// 파라미터로 전달받은 정답PanoramaImage의 타입의 한글문자열을 변수에 저장하는 부분 
			answerTypeHangulString = typeHangulMap.get(answerType);
			// 문제텍스트와 같은 경우 "[질문 : {type} 사진을 고르세요]" 형식으로 되어 있으며 이부분에서 "{type}"을 한글문자열로 replace하는 부분
			resultDescriptString = CommonImageInfo.descriptString.replace("{type}", answerTypeHangulString);	
			
			// 문제텍스트 이미지를 설정하는 부분으로 배경은 검은색이고 font는 흰색으로 설정하는 부분
			descriptImage = new BufferedImage(CommonImageInfo.width, CommonImageInfo.descriptHeight, BufferedImage.TYPE_INT_RGB);
			g = descriptImage.createGraphics();
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, CommonImageInfo.width, CommonImageInfo.height/3);
			g.setFont(new Font(CommonImageInfo.fontName, Font.BOLD, CommonImageInfo.fontSize));
			g.setColor(Color.WHITE);
			g.drawString(resultDescriptString, 0, CommonImageInfo.fontSize);
			
			// 문제텍스트 이미지와 캡차이미지를 합치는 부분
			resultCaptchaImage = new BufferedImage(CommonImageInfo.width, CommonImageInfo.height + CommonImageInfo.descriptHeight, BufferedImage.TYPE_INT_RGB);
			g = resultCaptchaImage.createGraphics();
			g.drawImage(descriptImage, 0, 0, null);
			g.drawImage(captchaImage, 0, CommonImageInfo.descriptHeight, null);
		}catch(Exception e) {
			log.error("[getDescriptImage] UserMessage  : 문제텍스트 이미지를 만드는 도중 에러발생");
			log.error("[getDescriptImage] SystemMessage: {}", e.getMessage());
			log.error("[getDescriptImage] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		// Graphics2D 라이브러리의 메모리를 반환하는 부분
		g.dispose();
		return resultCaptchaImage;
	}
	
	/*
	 * Func : 영어문자열타입 리스트와 한글문자열타입 리스트를 파라미터로 받아 영어문자을은 key로, 한글문자열은 value로하는 HashMap 전역변수에 값을 저장하는 함수
	 */
	public void setTypeHangulMap(List<String> typeList, List<String> typeHangulList) throws Exception{
		int typeListSize = 0;
		try {
			typeHangulMap = new HashMap<String, String>();
			typeListSize = typeList.size();
			for(int i=0; i < typeListSize; i++) {
				typeHangulMap.put(typeList.get(i), typeHangulList.get(i));
			}
		}catch(Exception e) {
			log.error("[setTypeHangulMap] UserMessage  : 각 타입에 해당하는 한글타입명을 HashMap객체에 저장하던 도중 에러발생");
			log.error("[setTypeHangulMap] SystemMessage: {}", e.getMessage());
			log.error("[setTypeHangulMap] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
}
