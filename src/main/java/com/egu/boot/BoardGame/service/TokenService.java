package com.egu.boot.BoardGame.service;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.egu.boot.BoardGame.config.security.JwtTokenProvider;
import com.egu.boot.BoardGame.handler.CustomException;
import com.egu.boot.BoardGame.handler.ErrorCode;
import com.egu.boot.BoardGame.model.User;
import com.egu.boot.BoardGame.model.dto.TokenDto.TokenRequestDto;
import com.egu.boot.BoardGame.model.dto.TokenDto.TokenResponseDto;
import com.egu.boot.BoardGame.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {
	
	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;

	@Transactional
	public TokenResponseDto 토큰재발급(HttpServletRequest request) {
		
		//헤더에서 refreshToken 꺼내기
		String refreshToken = jwtTokenProvider.resolveRefreshToken(request);
		
		//유효성 검사
		if(!jwtTokenProvider.validateRefreshToken(refreshToken)) {
			throw new CustomException(ErrorCode.INVALID_TOKEN);
		}
		//토큰으로부터 id 꺼내기
		int Id = Integer.parseInt(jwtTokenProvider.getId(refreshToken));

		//id값으로 User 조회
		User user = userRepository.findById(Id).orElseThrow(()->{
			throw new CustomException(ErrorCode.USER_NOT_FOUND);
		});
		
		//응답 객체 초기화
		TokenResponseDto responseDto = null;
	
		//토큰 재발급
		String reissuedRefreshToken = jwtTokenProvider.createRefreshToken(String.valueOf(user.getId()));
		System.out.println(reissuedRefreshToken);
		String reissuedAccessToken = jwtTokenProvider.createAccessToken(String.valueOf(user.getId()), user.getRoles());
		System.out.println(reissuedAccessToken);
		responseDto  = new TokenResponseDto(reissuedAccessToken, reissuedRefreshToken, "success");
		
		//user가 null이거나, 토큰이 유효하지 않다거나
//			throw new CustomException(ErrorCode.INVALID_TOKEN);
//		responseDto = new TokenResponseDto(null, null, "fail");
		
		return responseDto;
	}

}