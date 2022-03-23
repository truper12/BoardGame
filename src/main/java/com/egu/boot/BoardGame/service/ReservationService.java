package com.egu.boot.BoardGame.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.egu.boot.BoardGame.handler.CustomException;
import com.egu.boot.BoardGame.handler.ErrorCode;
import com.egu.boot.BoardGame.model.Branch;
import com.egu.boot.BoardGame.model.Payment;
import com.egu.boot.BoardGame.model.Reservation;
import com.egu.boot.BoardGame.model.Slot;
import com.egu.boot.BoardGame.model.Theme;
import com.egu.boot.BoardGame.model.User;
import com.egu.boot.BoardGame.model.dto.ReservationDto;
import com.egu.boot.BoardGame.model.dto.ReservationDto.ReservationRequestDto;
import com.egu.boot.BoardGame.model.dto.ReservationDto.ReservationResponseDto;
import com.egu.boot.BoardGame.repository.BranchRepository;
import com.egu.boot.BoardGame.repository.FindReservationRepository;
import com.egu.boot.BoardGame.repository.PaymentRepository;
import com.egu.boot.BoardGame.repository.ReservationRepository;
import com.egu.boot.BoardGame.repository.SlotRepository;
import com.egu.boot.BoardGame.repository.ThemeRepository;
import com.egu.boot.BoardGame.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {


	private final ReservationRepository reservationRepository;
	private final UserRepository userRepository;
	private final SlotRepository slotRepository;
	private final FindReservationRepository findReservationRepository;
	private final BranchRepository branchRepository;
	private final ThemeRepository themeRepository;
	private final PaymentRepository paymentRepository;

	@Transactional
	public ReservationResponseDto 예약등록(ReservationRequestDto reservationRequestDto) {
		Slot slot = slotRepository.findById(reservationRequestDto.getSlotId()).orElseThrow(() -> {
			throw new CustomException(ErrorCode.SLOT_NOT_FOUND);
		});
		if(slot.isReserved() == true) {	//중복 요청시
			throw new CustomException(ErrorCode.SLOT_ALEADY_RESERVED);
		}
		if(slot.isOpened() == false || slot.isShowed() == false) {	//닫힌 슬롯 혹은 비공개 슬롯시
			throw new CustomException(ErrorCode.SLOT_FORBIDDEN);
		}
		Branch branch = branchRepository.findById(reservationRequestDto.getBranchId()).orElseThrow(()->{
			throw new CustomException(ErrorCode.BRANCH_NOT_FOUND);
		});
		Theme theme = themeRepository.findById(reservationRequestDto.getThemeId()).orElseThrow(()->{
			throw new CustomException(ErrorCode.THEME_NOT_FOUND);
		});
		Payment payment = paymentRepository.findById(reservationRequestDto.getPaymentId()).orElseThrow(()->{
			throw new CustomException(ErrorCode.PAYMENT_NOT_FOUND);
		});
		Reservation reservation = new Reservation(reservationRequestDto);
		reservation.setBranch(branch);
		reservation.setPayment(payment);
		reservation.setSlot(slot);
		reservation.setTheme(theme);
		reservation.setReservationNumber(
				 makeReservationNumber(slot.getSlotDate(), theme.getId(), branch.getId(),  reservationRequestDto.getPhoneNum())
				);
		Reservation reserv = reservationRepository.save(reservation); 
		slot.setReserved(true); // 슬롯 예약됨으로 변경	
		
		return new ReservationResponseDto(reserv);
	}
	
	public String makeReservationNumber(LocalDate date, int themeId, int branchId, String phoneNum ) {
		//날짜 / 테마번호id / 지점id / 슬롯id / 예약자 뒷자리2개 / 난수 3개
		// ex) 202203231184939
		String phoneNumber = phoneNum.replaceAll("[^0-9]", "").substring(9);	//마지막 2자리
		int randomNumber = new Random().nextInt(999); //난수 3자리
		return date.format(DateTimeFormatter.BASIC_ISO_DATE)+
												Integer.toString(themeId) + Integer.toString(branchId)+phoneNumber+randomNumber;
	}

	@Transactional
	public ReservationResponseDto 예약조회(int id) {
		Reservation reservation = reservationRepository.findById(id).orElseThrow(()->{
				throw new CustomException(ErrorCode.RESERVATION_NOT_FOUND);
			});
		System.out.println(reservation.getBranch().getBranchName());
		return new ReservationResponseDto(reservation);
	}

	@Transactional
	public List<Reservation> 예약검색조회(String bookerName, String phoneNumber, Integer id) {
		List<Reservation> list =  findReservationRepository.searchReservation(bookerName, phoneNumber, id);
		return list;
	}
	
	
	
	
}
