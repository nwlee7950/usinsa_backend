package com.spring.usinsa.serviceImpl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.google.gson.Gson;
import com.spring.usinsa.dto.TokenDto;
import com.spring.usinsa.dto.oauth2.GoogleProfile;
import com.spring.usinsa.dto.oauth2.KakaoProfile;
import com.spring.usinsa.dto.oauth2.KakaoTokenDto;
import com.spring.usinsa.dto.oauth2.NaverProfile;
import com.spring.usinsa.exception.ApiErrorCode;
import com.spring.usinsa.exception.ApiException;
import com.spring.usinsa.model.Social;
import com.spring.usinsa.model.User;
import com.spring.usinsa.service.OAuthService;
import com.spring.usinsa.service.TokenService;
import com.spring.usinsa.service.UserService;
import com.spring.usinsa.util.GoogleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements OAuthService {

    private final UserService userService;
    private final TokenService tokenService;
    private final WebClient webClient;
    private final GoogleUtils googleUtils;
    private final Gson gson;

    // KAKAO
    @Value("${spring.oauth.kakao.client_id}")
    private String kakaoClientId;
    @Value("${spring.oauth.kakao.url.redirect}")
    private String kakaoRedirect;
    @Value("${spring.oauth.kakao.url.token}")
    private String kakaoTokenUrl;
    @Value("${spring.oauth.kakao.secret}")
    private String kakaoClientSecret;
    @Value("${spring.oauth.kakao.url.profile}")
    private String kakaoProfileUrl;

    // NAVER
    @Value("${spring.oauth.naver.client_id}")
    private String naverClientId;
    @Value("${spring.oauth.naver.secret}")
    private String naverSecret;
    @Value("${spring.oauth.naver.url.profile}")
    private String naverProfileUrl;
    @Value("${spring.oauth.naver.url.redirect}")
    private String naverRedirectUrl;

    @Override
    public TokenDto oauthLogin(String email, Social social, String socialId) {

        User user = userService.findFirstBySocialAndSocialId(social, socialId);

        // 이메일 주소가 다를 경우 바뀐 이메일로 최신화 (즉, 이전에 구글로 회원가입했지만, 구글 계정 이메일 주소가 바뀐 경우)
        if(!email.equals(user.getEmail())) {
            user.setEmail(email);
            userService.signUp(user);
        }

        // 정상 로그인 처리 (Access Token, Refresh Token 발급)
        TokenDto tokenDto = tokenService.saveToken(user);

        return tokenDto;
    }

    @Override
    public void guideSocial(String email) {
        User user = userService.findFirstByEmail(email);

        // 해당 이메일로 가입한 소셜 알림
        if(Social.USINSA.getValue().equals(user.getSocial()))
            throw new ApiException(ApiErrorCode.USINSA_USER);
        else if(Social.KAKAO.getValue().equals(user.getSocial()))
            throw new ApiException(ApiErrorCode.KAKAO_USER);
        else if(Social.NAVER.getValue().equals(user.getSocial()))
            throw new ApiException(ApiErrorCode.NAVER_USER);
        else
            throw new ApiException(ApiErrorCode.GOOGLE_USER);
    }

    @Override
    public KakaoProfile getKakaoProfile(String accessToken) {;
        try {
            // 카카오 사용자 프로필 정보
            String response = webClient.post()
                    .uri(kakaoProfileUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve() // 4xx, 5xx 에러 발생시 자동으로 예외 발생시킴. (토큰으로 사용자 프로필을 가져오지 못한 경우)
                    .bodyToMono(String.class)   // body 만 가져옴
                    .block();   // 동기 처리

            return gson.fromJson(response, KakaoProfile.class);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);
        }
    }

    @Override
    public NaverProfile getNaverProfile(String accessToken) {
        try {
            // 네이버 사용자 프로필 정보
            String response = webClient.post()
                    .uri(naverProfileUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Naver-Client-Id", naverClientId)
                    .header("X-Naver-Client-Secret", naverSecret)
                    .contentType(MediaType.TEXT_PLAIN)
                    .retrieve() // 4xx, 5xx 에러 발생시 자동으로 예외 발생시킴. (토큰으로 사용자 프로필을 가져오지 못한 경우)
                    .bodyToMono(String.class)   // body 만 가져옴
                    .block();   // 동기 처리

            return gson.fromJson(response, NaverProfile.class);

        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);
        }
    }

    @Override
    public KakaoTokenDto getKakaoTokenInfo(String code) {

        try {
            // 카카오 사용자 프로필 정보
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(kakaoTokenUrl)
                            .queryParam("grant_type", "authorization_code")
                            .queryParam("client_id", "kakaoClientId")
                            .queryParam("redirect_uri", "kakaoRedirect")
                            .queryParam("code", "code")
                            .queryParam("client_secret", "kakaoClientSecret")
                            .build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve() // 4xx, 5xx 에러 발생시 자동으로 예외 발생시킴. (인가 코드로 토큰을 받아오지 못한 경우)
                    .bodyToMono(String.class)   // body 만 가져옴
                    .block();   // 동기 처리

            return gson.fromJson(response, KakaoTokenDto.class);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);
        }
    }

    @Override
    public ResponseEntity<Object> getGoogleLoginForm() {
        String authUrl = googleUtils.googleInitUrl();
        URI redirectUri = null;
        try {
            redirectUri = new URI(authUrl);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setLocation(redirectUri);
            return new ResponseEntity<>(httpHeaders, HttpStatus.SEE_OTHER);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return ResponseEntity.badRequest().build();
    }

//    @Override
//    public String getGoogleIdToken(String code) {
        /* 프론트단에서 진행할 인가코드로 토큰을 받아오는 과정
        *
        *
        // HTTP 통신을 위해 RestTemplate 활용
        RestTemplate restTemplate = new RestTemplate();

        // 토큰 발급 요청할 Dto 빌드
        GoogleLoginRequestDto requestParams = GoogleLoginRequestDto.builder()
                .clientId(googleUtils.getGoogleClientId())
                .clientSecret(googleUtils.getGoogleSecret())
                .code(code)
                .redirectUri(googleUtils.getGoogleRedirectUri())
                .grantType("authorization_code")
                .build();

        // Http Header 설정 -> 토큰 발급 요청
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GoogleLoginRequestDto> httpRequestEntity = new HttpEntity<>(requestParams, headers);
        ResponseEntity<String> apiResponseJson = restTemplate.postForEntity(googleUtils.getGoogleAuthUrl() + "/token", httpRequestEntity, String.class);

         ObjectMapper 를 통해 발급된 토큰을(String) Object 로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // NULL이 아닌 값만 응답받기(NULL인 경우는 생략)
        GoogleTokenDto googleTokenDto = objectMapper.readValue(apiResponseJson.getBody(), new TypeReference<GoogleTokenDto>() {});

        // 사용자의 정보는 JWT Token으로 저장되어 있고, Id_Token에 값을 저장한다.
        return googleTokenDto.getIdToken();
        *
        *
        */
//    }

    @Override
    public GoogleProfile getGoogleProfile(String idToken) throws JsonProcessingException {

        String requestUrl = UriComponentsBuilder.fromHttpUrl(googleUtils.getGoogleAuthUrl() + "/tokeninfo").queryParam("id_token", idToken).toUriString();

        // 토큰의 id_Token을 전달해 토큰에 저장된 구글 사용자 프로필 정보 확인
        String response = webClient.get()
                .uri(requestUrl)
                .retrieve() // 4xx, 5xx 에러 발생시 자동으로 예외 발생시킴. (인가 코드로 토큰을 받아오지 못한 경우)
                .bodyToMono(String.class)   // body 만 가져옴
                .block();   // 동기 처리

        if(response == null)
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);

        // ObjectMapper 를 통해 사용자 정보인 response 를 Object(GoogleProfile 객체) 로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // NULL이 아닌 값만 응답받기(NULL인 경우는 생략)

        return objectMapper.readValue(response, new TypeReference<GoogleProfile>() {});
    }

    @Override
    public Object checkProfile(Social social, String socialId, String email, Object object) {
        // 이미 해당 소셜로 가입한 사용자일 경우
        if(userService.existsBySocialAndSocialId(social, socialId)) {
            TokenDto tokenDto = oauthLogin(email, social, socialId);
            return tokenDto;
        }

        // 해당 이메일로 가입된 다른 소셜이 존재할 경우
        else if(userService.existsByEmail(email)){
            guideSocial(email);
            return null;
        }

        // 해당 이메일, 소셜로 가입된 사용자가 없을 경우 (신규 회원)
        else {
            return object;
        }
    }
}
