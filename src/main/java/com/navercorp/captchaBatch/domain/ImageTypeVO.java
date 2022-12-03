package com.navercorp.captchaBatch.domain;

public class ImageTypeVO {
	private int typeKey;			// var : PanoramaImage의 타입(school, bridge..) 키를 저장하는 변수
	private String typeName;		// var : PanoramaImage의 타입명을 저장하는 변수
	
	public int getTypeKey() {
		return typeKey;
	}
	public void setTypeKey(int typeKey) {
		this.typeKey = typeKey;
	}
	public String getTypeName() {
		return typeName;
	}
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
}
