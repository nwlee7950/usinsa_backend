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

        // ????????? ????????? ?????? ?????? ?????? ???????????? ????????? (???, ????????? ????????? ?????????????????????, ?????? ?????? ????????? ????????? ?????? ??????)
        if(!email.equals(user.getEmail())) {
            user.setEmail(email);
            userService.signUp(user);
        }

        // ?????? ????????? ?????? (Access Token, Refresh Token ??????)
        TokenDto tokenDto = tokenService.saveToken(user);

        return tokenDto;
    }

    @Override
    public void guideSocial(String email) {
        User user = userService.findFirstByEmail(email);

        // ?????? ???????????? ????????? ?????? ??????
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
            // ????????? ????????? ????????? ??????
            String response = webClient.post()
                    .uri(kakaoProfileUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve() // 4xx, 5xx ?????? ????????? ???????????? ?????? ????????????. (???????????? ????????? ???????????? ???????????? ?????? ??????)
                    .bodyToMono(String.class)   // body ??? ?????????
                    .block();   // ?????? ??????

            return gson.fromJson(response, KakaoProfile.class);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);
        }
    }

    @Override
    public NaverProfile getNaverProfile(String accessToken) {
        try {
            // ????????? ????????? ????????? ??????
            String response = webClient.post()
                    .uri(naverProfileUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Naver-Client-Id", naverClientId)
                    .header("X-Naver-Client-Secret", naverSecret)
                    .contentType(MediaType.TEXT_PLAIN)
                    .retrieve() // 4xx, 5xx ?????? ????????? ???????????? ?????? ????????????. (???????????? ????????? ???????????? ???????????? ?????? ??????)
                    .bodyToMono(String.class)   // body ??? ?????????
                    .block();   // ?????? ??????

            return gson.fromJson(response, NaverProfile.class);

        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);
        }
    }

    @Override
    public KakaoTokenDto getKakaoTokenInfo(String code) {

        try {
            // ????????? ????????? ????????? ??????
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
                    .retrieve() // 4xx, 5xx ?????? ????????? ???????????? ?????? ????????????. (?????? ????????? ????????? ???????????? ?????? ??????)
                    .bodyToMono(String.class)   // body ??? ?????????
                    .block();   // ?????? ??????

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
        /* ?????????????????? ????????? ??????????????? ????????? ???????????? ??????
        *
        *
        // HTTP ????????? ?????? RestTemplate ??????
        RestTemplate restTemplate = new RestTemplate();

        // ?????? ?????? ????????? Dto ??????
        GoogleLoginRequestDto requestParams = GoogleLoginRequestDto.builder()
                .clientId(googleUtils.getGoogleClientId())
                .clientSecret(googleUtils.getGoogleSecret())
                .code(code)
                .redirectUri(googleUtils.getGoogleRedirectUri())
                .grantType("authorization_code")
                .build();

        // Http Header ?????? -> ?????? ?????? ??????
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GoogleLoginRequestDto> httpRequestEntity = new HttpEntity<>(requestParams, headers);
        ResponseEntity<String> apiResponseJson = restTemplate.postForEntity(googleUtils.getGoogleAuthUrl() + "/token", httpRequestEntity, String.class);

         ObjectMapper ??? ?????? ????????? ?????????(String) Object ??? ??????
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // NULL??? ?????? ?????? ????????????(NULL??? ????????? ??????)
        GoogleTokenDto googleTokenDto = objectMapper.readValue(apiResponseJson.getBody(), new TypeReference<GoogleTokenDto>() {});

        // ???????????? ????????? JWT Token?????? ???????????? ??????, Id_Token??? ?????? ????????????.
        return googleTokenDto.getIdToken();
        *
        *
        */
//    }

    @Override
    public GoogleProfile getGoogleProfile(String idToken) throws JsonProcessingException {

        String requestUrl = UriComponentsBuilder.fromHttpUrl(googleUtils.getGoogleAuthUrl() + "/tokeninfo").queryParam("id_token", idToken).toUriString();

        // ????????? id_Token??? ????????? ????????? ????????? ?????? ????????? ????????? ?????? ??????
        String response = webClient.get()
                .uri(requestUrl)
                .retrieve() // 4xx, 5xx ?????? ????????? ???????????? ?????? ????????????. (?????? ????????? ????????? ???????????? ?????? ??????)
                .bodyToMono(String.class)   // body ??? ?????????
                .block();   // ?????? ??????

        if(response == null)
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);

        // ObjectMapper ??? ?????? ????????? ????????? response ??? Object(GoogleProfile ??????) ??? ??????
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // NULL??? ?????? ?????? ????????????(NULL??? ????????? ??????)

        return objectMapper.readValue(response, new TypeReference<GoogleProfile>() {});
    }

    @Override
    public Object checkProfile(Social social, String socialId, String email, Object object) {
        // ?????? ?????? ????????? ????????? ???????????? ??????
        if(userService.existsBySocialAndSocialId(social, socialId)) {
            TokenDto tokenDto = oauthLogin(email, social, socialId);
            return tokenDto;
        }

        // ?????? ???????????? ????????? ?????? ????????? ????????? ??????
        else if(userService.existsByEmail(email)){
            guideSocial(email);
            return null;
        }

        // ?????? ?????????, ????????? ????????? ???????????? ?????? ?????? (?????? ??????)
        else {
            return object;
        }
    }
}
