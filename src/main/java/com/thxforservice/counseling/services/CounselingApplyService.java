package com.thxforservice.counseling.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thxforservice.counseling.constants.CCase;
import com.thxforservice.counseling.constants.Status;
import com.thxforservice.counseling.controllers.RequestCounselingApply;
import com.thxforservice.counseling.entities.Counseling;
import com.thxforservice.counseling.exceptions.CounselingNotFoundException;
import com.thxforservice.counseling.repositories.CounselingRepository;
import com.thxforservice.global.exceptions.BadRequestException;
import com.thxforservice.global.rests.ApiRequest;
import com.thxforservice.member.MemberUtil;
import com.thxforservice.member.entities.Member;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CounselingApplyService {

    private final CounselingRepository counselingRepository;
    private final MemberUtil memberUtil;
    private final CounselingStatusService counselingStatusService;
    private final ApiRequest apiRequest;

    @Transactional
    public Counseling apply(RequestCounselingApply form) {

//         로그인 확인 및 날짜 유효성 검사
        if (!memberUtil.isLogin() || form.getRDate() == null || form.getRDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("올바른 접근이 아닙니다.");
        }

        // studentNo로 기존 상담이 있는지 확인
//        Long studentNo = form.getStudentNo();
//        Counseling findCounseling = counselingRepository.findById(studentNo).orElseThrow(CounselingNotFoundException::new);

        // 상담 이력이 있으면 기존 Gid, 없으면 Gid 생성
//        String gid = (findCounseling != null) ? findCounseling.getGid() : generateGID(studentNo);

//        String gid = generateGID(studentNo);

        /* 잊지마라 너의 부족함을 S */
//        if (findCounseling != null) {
//            gid = findCounseling.getGid();
//        } else {
//            gid = generateGID(studentNo);
//        }
        /* 잊지마라 너의 부족함을 E */

        // 사용자 정보 및 상담 세션 검증
        String mobile = form.getMobile();
        if (StringUtils.hasText(mobile)) {
            mobile = mobile.replaceAll("\\D", ""); // 전화번호에서 숫자만 추출
        }

        // 새로운 상담 예약 생성
        Counseling counseling = Counseling.builder()
//                .gid(gid)  // GID 설정
                .username(form.getUsername()) // 내담자 이름
                .studentNo(form.getStudentNo()) // 학번 설정
                .cCase(form.getCCase()) // 상담 유형
                .customCase(form.getCustomCase()) // 기타 선택시 입력
                .cReason(form.getCReason()) // 상담 경위
                .rDate(form.getRDate()) // 예약일
                .rTime(form.getRTime()) // 예약 시간
                .email(form.getEmail()) // 이메일
                .mobile(mobile) // 전화번호
                .build();

        // '기타' 상담 유형을 선택한 경우 customCase 값 설정
        if (form.getCCase() == CCase.OTHER) {
            counseling.setCustomCase(form.getCustomCase()); // customCase 설정
        }

        // DB에 저장 및 플러시
        counselingRepository.saveAndFlush(counseling);

        counselingStatusService.change(counseling.getCSeq(), Status.APPLY);

        return counseling;
    }

    // GID 생성 (studentNo를 기반으로 생성)
    private String generateGID(Long studentNo) {
        return UUID.randomUUID().toString();
    }

    @Transactional
    public void applyRandom(Long cSeq) {
        // 상담 예약 정보 조회
        Counseling counseling = counselingRepository.findById(cSeq)
                .orElseThrow(() -> new CounselingNotFoundException());

        // 상담사가 이미 배정된 경우 배정 생략
        if (counseling.getEmpNo() != null) {
            return;
        }

        // 상담사 목록 API 호출
        List<Member> counselors = apiRequest.request("/account/counselors", "memberservice")
                .toList(new TypeReference<List<Member>>() {
                });

        // 로그 추가
        System.out.printf("상담사 목록 API 응답: {}", counselors);

        // 상담사 목록이 비어있으면 예외 처리
        if (counselors == null || counselors.isEmpty()) {
            throw new CounselingNotFoundException();
        }

        // 상담사 상태 (휴직, 퇴직) 필터링
        counselors = counselors.stream()
                .filter(counselor -> counselor.getStatus() == com.thxforservice.member.constants.Status.EMPLOYED) // member.constants.Status로 변경
                .collect(Collectors.toList());

        // 상담사 목록이 비어있으면 예외 처리
        if (counselors.isEmpty()) {
            throw new CounselingNotFoundException();
        }

        // 랜덤으로 상담사 선택
        Random random = new Random();
        Member selectedCounselor = counselors.get(random.nextInt(counselors.size()));

        // 상담 예약에 선택된 상담사 배정
        counseling.setEmpNo(String.valueOf(selectedCounselor.getEmpNo()));

        // 예약 정보 저장
        counselingRepository.saveAndFlush(counseling);
    }
}
