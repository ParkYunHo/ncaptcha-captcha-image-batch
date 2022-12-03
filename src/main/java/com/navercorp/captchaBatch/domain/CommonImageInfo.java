package com.navercorp.captchaBatch.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CommonImageInfo {
	// project environment
	public static final String projectEnvironment = "dev";								// var : Captcha Server가 실행되는 환경을 저장하는 변수 (dev, stage, real)
	public static boolean isAvailableRedis = true; 										// var : Redis Server가 현재 사용가능한지 유무를 저장하는 변수

	// Image Info
	public static final int width = 300;												// var : 캡차이미지의 전체 가로길이를 저장하는 변수
	public static final int height = 300;												// var : 캡차이미지의 전체 세로길이를 저장하는 변수
	public static final int imageCellSize = 100;										// var : 캡차이미지를 구성하는 각각 9개의 정사각형 한 변의 길이를 저장하는 변수 
	public static final int fontSize = 15;												// var : 문제텍스트의 폰트크기를 저장하는 변수						
	public static final int descriptHeight = 30;										// var : 문제텍스트 이미지의 세로높이값을 저장하는 변수
	public static final String typeArray[] = {											// var : 이미지의 타입을 배열형식으로 저장한 변수 (초기화의 편의성을 위해 배열선언)
			"school", "bridge", "crossroad", "apartment", "gas-station", 
			"convenience-store", "fire-station", "subway-station", "daiso"};
	public static final String typeHangulArray[] = {									// var : 이미지 타입의 한글텍스트를 배열형식으로 저장한 변수 (문제텍스트 이미지를 만들기 위해 선언)
			"학교", "다리", "건널목", "아파트", "주유소", 
			"편의점", "소방서", "지하철역", "다이소"};
	public static final List<String> typeList = Arrays.asList(typeArray);				// var : 배열타입으로 선언된 이미지 타입들을 리스트형식으로 저장하는 변수 (사용시의 편의를 위해 리스트 생성)
	public static final String typeResizedArray[] = {									// var : 이미지 타입명의 앞 두자리만 사용한 타입을 배열형식으로 저장한 변수
			"sc", "br", "cr", "ap", "ga", "co", "fi", "su", "da"};
	public static final List<String> typeResizedList = Arrays.asList(typeResizedArray);	// var : Resized된 이미지타입 배열을 리스트형식으로 저장하는 변수 (사용시의 편의를 위해 리스트 생성) 
	
	// Pool Header
	public static final String examPanoramaImagePoolKeyHeader = "e_";					// var : 보기 PanoramaImage들을 저장하고 있는 리스트의 헤더부분을 저장하는 변수
	public static final String answerPanoramaImagePoolKeyHeader = "a_";					// var : 정답 PanoramaImage들을 저장하고 있는 리스트의 헤더부분을 저장하는 변수
	public static final String captchaImagePoolKeyHeader = "cil_";						// var : 일반모드의 CaptchaImage들을 저장하고 있는 리스트의 헤더부분을 저장하는 변수
	public static final String captchaImageClientPoolKeyHeader = "cl_";					// var : CaptchaImage에 관여한 사용자의 ClientKey들을 저장하는 리스트의 헤더부분을 저장하는 변수
	public static final String captchaImageHistoryPoolKeyHeader = "his_";				// var : CaptchaImage에 대해 사용자의 Action(SUCCESS, FAIL, REFRESH) 히스토리들을 저장하는 리스트의 헤더부분을 저장하는 변수
	
	// Field
	public static final String base64Field = "b64";										// var : Redis의 Hash형식의 CaptchaImage데이터에서 Base64필드명을 저장하는 변수
	public static final String captchaImageIssuedCntField = "ic";						// var : Redis의 Hash형식의 CaptchaImage에서 발행횟수를 저장하고 있는 필드명을 저장하는 변수
	public static final String captchaImageScoreField = "score";						// var : Redis의 Hash형식의 CaptchaImage에서 점수(Score)값을 저장하고 있는 필드명을 저장하는 변수
	public static final String captchaImageClientPoolField = "cl";						// var : Redis의 Hash형식의  CaptchaImage에서 CaptchaImage에 관여한 사용자의 ClientKey들을 저장하는 리스트의 키값을 저장하고 있는 필드명을 저장하는 변수
	public static final String captchaImageHistoryPoolField = "his";					// var : Redis의 Hash형식의  CaptchaImage에서  CaptchaImage에 대해 사용자의 Action(SUCCESS, FAIL, REFRESH) 히스토리들을 저장하는 리스트의 키값을 저장하고 있는 필드명을 저장하는 변수
	public static final String answerXAxisField = "ax";									// var : Redis의 Hash형식의 CaptchaImage 데이터에서 정답좌표의 X축 필드명을 저장하는 변수
	public static final String answerYAxisField = "ay";									// var : Redis의 Hash형식의  CaptchaImage 데이터에서 정답좌표의 Y축 필드명을 저장하는 변수
	
	// Full String
	public static final String descriptString = "[질문 : {type} 사진을 고르세요]";				// var : 문제 텍스트 문자열을 저장하는 변수 (가운데 있는 {type}은 replace를 통해 동적으로 type한글명을 사용하기 위해 해당 방식 사용)
	public static final String fontName = "Gungsuh";									// var : 캡차이미지의 문제텍스트 폰트명을 저장하는 변수 (다른 폰트명과 같은 경우 한글이 깨질 위험성이 있어 해당 폰트명으로 고정)
	public static final String imageFormatName = "jpeg";								// var : BufferedImage를 base64로 변환할때 format문자열을 저장하는 변수		
	public static final String answerString = "answer";									// var : 정답PanoramaImage의 키값 설정을 위한 변수
	public static final String examString = "exam";										// var : 보기PanoramaImage의 키값 설정을 위한 변수

	// CaptchaImage RGB Range
	public static final int minTotalAverageValue = 20;									// var : CaptchaImage를 만들때 사용되는 보기이미지의 최소 RGB평균값을 저장하는 변수 (너무 어두운 이미지를 제외하기 위해)
	public static final int maxTotalAverageValue = 230;									// var : CaptchaImage를 만들때 사용되는 보기이미지의 최대 RGB평균값을 저장하는 변수 (너무 밝은 이미지를 제외하기 위해)
	public static final int maxBlueAverageValue = 240;									// var : CaptchaImage를 만들때 사용되는 보기이미지의 최대 Blue평균값을 저장하는 변수 (하늘만 보이는 이미지를 제외하기 위해)
	public static final int maxStandardDeviationValue = 15;								// var : CaptchaImage를 만들때 사용되는 보기이미지의 표준편차의 최대값을 저장하는 변수 (표준편차가 너무 적으면 한가지 색깔만 보이는 보기이미지가 출력되므로 해당 값보다 표준편차가 적은 보기이미지는 사용하지 않는다) 
	
	// ETC
	public static final String emptyValue = "0";										// var : ClientKey의 RefreshCnt, FailCnt 등을 초기화할때 사용하기 위해 "0"을 문자열 형식으로 저장하는 변수										
	public static final int maxCaptchaImagePoolSize = 8;								// var : CaptchaImagePool의 최대 크기를 저장하는 변수
	public static final int examPanoramaImageCnt = 2;									// var : CaptchaImage를 만들때 사용되는 보기PanoramaImage의 개수를 저장하는 변수
	public static Map<String, Integer> emptyAnswerParnoamaImageMap;						// var : CaptchaImage를 만들때 부족한 정답PanoramaImage의 개수를 다음 Step에게 전달하기 위한 변수
	public static Map<String, Integer> emptyExamParnoamaImageMap;						// var : CaptchaImage를 만들때 부족한 보기PanoramaImage의 개수를 다음 Step에게 전달하기 위한 변수
}
