package com.navercorp.captchaBatch.domain;

public class ImageInfoVO {
	private String imageKey;		// var : MySQL내에 저장된 Panorama Image의 기본키를 저장하기 위한 변수
	private String typeName;		// var : MySQL내에 저장된 Panorama Image의  타입(school, bridge..)을 저장하기 위한 변수
	private String usageName;		// var : MySQL내에 저장된 Panorama Image의 용도(answer, exam)을 저장하기 위한 변수
	private String b64;				// var : MySQL내에 저장된 Panorama Image의 Base64 정보를 저장하기 위한 변수
	private int emptyCnt;			// var : CaptchaImagePool을 구성하기에 부족한 개수를 저장할 변수				
	
	public String getImageKey() {
		return imageKey;
	}
	public void setImageKey(String imageKey) {
		this.imageKey = imageKey;
	}
	public String getUsageName() {
		return usageName;
	}
	public void setUsageName(String usageName) {
		this.usageName = usageName;
	}
	public int getEmptyCnt() {
		return emptyCnt;
	}
	public void setEmptyCnt(int emptyCnt) {
		this.emptyCnt = emptyCnt;
	}
	public String getTypeName() {
		return typeName;
	}
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	public String getB64() {
		return b64;
	}
	public void setB64(String b64) {
		this.b64 = b64;
	}
}
