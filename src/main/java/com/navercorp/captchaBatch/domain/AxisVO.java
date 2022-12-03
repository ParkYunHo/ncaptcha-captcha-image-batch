package com.navercorp.captchaBatch.domain;

public class AxisVO {
	private	int xAxis;		// var : 정답/시작좌표의 x축을 저장하기 위한 변수
	private int yAxis;		// var : 정답/시작좌표의 y축을 저장하기 위한 변수
	
	public int getxAxis() {
		return xAxis;
	}
	public void setxAxis(int xAxis) {
		this.xAxis = xAxis;
	}
	public int getyAxis() {
		return yAxis;
	}
	public void setyAxis(int yAxis) {
		this.yAxis = yAxis;
	}
}
