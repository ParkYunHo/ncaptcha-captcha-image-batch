package com.navercorp.captchaBatch.mapper;

import java.util.List;

import com.navercorp.captchaBatch.domain.ImageInfoVO;
import com.navercorp.captchaBatch.domain.ImageTypeVO;

public interface ImageMapper {
	// (select) 이미지 타입정보(school, bridge..)를 저장하고 있는 MySQL의 image_type Table에 저장된 모든 row를 가져오는 메서드
	public List<ImageTypeVO> selectImageTypeList() throws Exception;
	// (select) paramList 변수에 저장된 타입별 부족한 개수만큼 MySQL에서 정답 PanoramaImage를 가져오는 메서드 
	public List<ImageInfoVO> selectImageInfoList(String usageName, List<ImageInfoVO> paramList) throws Exception;
	// (update) paramList 변수에 저장된 타입별 부족한 개수만큼 MySQL에서 가져간 정답 PanoramaImage의 IsUsed 필드를 '1'로 변경하는 메서드
	//			Batch Job발생시 MySQL내에서 Redis로 가져와진 이미지(IsUsed=1) row들을 모두 삭제하기 위해 사용
	public void updateImageInfoList(String usageName, List<ImageInfoVO> paramList) throws Exception;
	// (select) MySQL에서 가져온 정답 PanoramaImage의 키를 삭제된 CaptchaImage를 저장하고 있는 unused_info Table에서 검색하고 해당 개수를 리턴하는 메서드
	//			만약 해당 키가 unused_info Table에 포함되어 있다면 그 이미지는 사용하지 않는다. 
	public int selectCheckUnusedImage(String imageKey) throws Exception;
}
