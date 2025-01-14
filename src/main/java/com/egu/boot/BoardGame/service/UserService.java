package com.egu.boot.BoardGame.service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestBody;

import com.egu.boot.BoardGame.config.security.JwtTokenProvider;
import com.egu.boot.BoardGame.handler.CustomException;
import com.egu.boot.BoardGame.handler.ErrorCode;
import com.egu.boot.BoardGame.model.Reservation;
import com.egu.boot.BoardGame.model.RoleType;
import com.egu.boot.BoardGame.model.User;
import com.egu.boot.BoardGame.model.dto.ReservationDto.ReservationResponseDto;
import com.egu.boot.BoardGame.model.dto.UserDto.UserRequestDto;
import com.egu.boot.BoardGame.model.dto.UserDto.UserResponseDto;
import com.egu.boot.BoardGame.repository.QUserRepository;
import com.egu.boot.BoardGame.repository.QUserRepositoryImpl;
import com.egu.boot.BoardGame.repository.UserRepository;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final QUserRepositoryImpl quserRepository;
	private final EntityManager entityManager;

	@Transactional
	public User 회원가입(UserRequestDto requestDto) {
		
		//마지막으로 다시 한번 중복 체크 
		User user= quserRepository.findUserByUserInfo(requestDto);
		if(!Objects.isNull(user)) throw new CustomException(ErrorCode.USERINFO_ALREADY_USED);
		
		//체크 후 가입 처리
		String rawPassword = requestDto.getPassword();
		String encPassword = passwordEncoder.encode(rawPassword);
		try {
			user = User.builder()
					.userId(requestDto.getUserId())
					.password(encPassword)
					.provider("Application")
					.isEnabled(true)
					.nickname(requestDto.getNickname())
					.roles(Collections.singletonList("ROLE_USER"))
					.createDate(LocalDateTime.now())
					.phoneNumber(requestDto.getPhoneNum())
					.privacyAgree(requestDto.getPrivacyAgree())
					.prAgree(requestDto.getPrAgree())
					.build();
		} catch (NullPointerException e) {
			throw new CustomException(ErrorCode.USERINFO_NOT_ENOUGH);
		}
		
		return userRepository.save(user);
	}

	@Transactional
	public Page<User> 회원리스트찾기(Pageable pageable) {
		Page<User> list = userRepository.findAll(pageable);
		return list;
	}

	@Transactional
	public UserResponseDto 회원정보수정(UserRequestDto requestDto) {	 
		User user = null;
		//시큐리티 컨텍스트에서 유저 정보 가져오기
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth == null || !(auth.getPrincipal() instanceof User)) {
			throw new CustomException(ErrorCode.REQUEST_RELOGIN);
		}
		user = (User)auth.getPrincipal();
		if(!ObjectUtils.isEmpty(requestDto.getPassword())) {
			//(2-1)비밀번호 변경시 - currentPw와 로그인 회원 pw 비교
			if(!passwordEncoder.matches(requestDto.getPassword(), user.getPassword()))
				throw new CustomException(ErrorCode.INVALID_PASSWORD);	
		}else {
			//(2-2)비밀번호 제외 회원 정보 변경시 - 마지막으로 다시 한번 중복 체크 
			if(!Objects.isNull(quserRepository.findUserByUserInfo(requestDto))) 
				throw new CustomException(ErrorCode.USERINFO_ALREADY_USED);
		}
		//(a)영속성 컨텍스트에 반영하기 위해 우선 준영속화
		entityManager.detach(user);
		
		//(3)유저 정보 수정
		long updateCount = quserRepository.modifyUserInfo(requestDto, user.getId());
		//(b)앞에서 준영속화되었기 때문에 find로 DB에서 꺼내옴 -> 수정된 entity를  영속화 
		User updatedUser= userRepository.findById(user.getId()).<CustomException>orElseThrow(()->{
			throw new CustomException(ErrorCode.USERINFO_CHANGE_FAILED);
		});
		if(updateCount > 0) {
			return new UserResponseDto(updatedUser);
		}else {
				throw new CustomException(ErrorCode.USERINFO_CHANGE_FAILED);
		}
	}
	
	//일반 회원탈퇴 (+ 소셜 회원탈퇴에도)
	@Transactional
	public long 회원탈퇴(UserRequestDto requestDto) {
		User user = null;
		//시큐리티 컨텍스트에서 유저 정보 가져오기
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth == null || !(auth.getPrincipal() instanceof User)) {
			throw new CustomException(ErrorCode.REQUEST_RELOGIN);
		}
		user = (User) auth.getPrincipal();
		//탈퇴 여부 재확인
		if(user.getDeactivatedDate() != null || user.isEnabled() != true) {
			throw new CustomException(ErrorCode.USER_DISABLED);
		}
		//일반 회원탈퇴의 경우 - 비밀번호 재입력 비밀번호 체크
		if(requestDto != null) {
			if(!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
				throw new CustomException(ErrorCode.INVALID_PASSWORD);
			}
		}
//		//영속성 컨텍스트 작업 (시큐리티 컨텍스트에서 꺼내온 user로는 더티체킹으로 수정불가)
//		User userPersist = userRepository.findById(user.getId()).orElseThrow(()->{
//			throw new CustomException(ErrorCode.USER_NOT_FOUND);
//		});
//		userPersist.setDeactivatedDate(LocalDateTime.now());
//		userPersist.setEnabled(false);
		long result = quserRepository.deactivateUser(user.getId());
		entityManager.close();
		return result;
	}
	

	@Transactional
	public UserResponseDto 로그인(String userId, String pw) {
		// (1) 회원 조회 
		User user = userRepository.findByUserIdAndProvider(userId, "Application").<CustomException>orElseThrow(()->{
			throw new CustomException(ErrorCode.USER_NOT_FOUND);
		});
		//(2-1) 회원 상태 체크
		if(user.isEnabled() == false) throw new CustomException(ErrorCode.USER_DISABLED);
		//(2-2) 패스워드 검증 전 체크
		if(pw == null || pw.equals("")) throw new CustomException(ErrorCode.USER_NOT_FOUND);
		//(2-3) 패스워드 검증
		if(!passwordEncoder.matches(pw, user.getPassword())) throw new CustomException(ErrorCode.USER_NOT_FOUND);
		// (3) 토큰 생성
		String accessToken = jwtTokenProvider.createAccessToken(String.valueOf(user.getId()), user.getRoles());
		String refreshToken = jwtTokenProvider.createRefreshToken(String.valueOf(user.getId()));
		//(4) 토큰 반환	
		return new UserResponseDto(accessToken, refreshToken);
	}
	
	@Transactional
	public User 회원정보로찾기(UserRequestDto requestDto){
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = null;
		if(auth != null && auth.getPrincipal() instanceof User) {
			//로그인 유저
			user = (User)auth.getPrincipal();
		}else {
			//비로그인 유저
			user= quserRepository.findUserByUserInfo(requestDto);
		}
		return user;
	}

	

	
	
}
