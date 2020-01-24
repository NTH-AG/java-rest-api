package com.messagebird.spring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.messagebird.APIResponse;
import com.messagebird.MessageBirdServiceImpl;
import com.messagebird.exceptions.GeneralException;
import com.messagebird.exceptions.NotFoundException;
import com.messagebird.exceptions.UnauthorizedException;
import org.apache.http.impl.client.HttpClientBuilder;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class MessageBirdServiceSpring extends MessageBirdServiceImpl {

	private final RestTemplate restTemplate;

	public MessageBirdServiceSpring(RestTemplateBuilder restTemplateBuilder, String accessKey, String serviceUrl) {
		super(accessKey, serviceUrl);
		this.restTemplate = createRestTemplate(restTemplateBuilder);
	}

	public MessageBirdServiceSpring(RestTemplateBuilder restTemplateBuilder, String accessKey) {
		super(accessKey);
		this.restTemplate = createRestTemplate(restTemplateBuilder);
	}

	protected RestTemplate createRestTemplate(RestTemplateBuilder restTemplateBuilder) {
		return restTemplateBuilder
				.requestFactory(this::createClientHttpRequestFactory)
				.errorHandler(new NoOpResponseErrorHandler())
				.defaultHeader("Authorization", "AccessKey " + getAccessKey())
				.build();
	}

	protected HttpComponentsClientHttpRequestFactory createClientHttpRequestFactory() {
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
				.useSystemProperties()
				.setUserAgent(getUserAgentString())
				.disableDefaultUserAgent()
				.disableContentCompression();
		return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
	}

	static class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
		public void handleError(ClientHttpResponse response) throws IOException {

		}
	}

	/**
	 * Actually sends a HTTP request and returns its body and HTTP status code.
	 *
	 * @param method  HTTP method.
	 * @param url     Absolute URL.
	 * @param payload Payload to JSON encode for the request body. May be null.
	 * @param <P>     Type of the payload.
	 * @return APIResponse containing the response's body and status.
	 */
	@Override
	protected <P> APIResponse doRequest(final String method, final String url, final P payload) throws GeneralException {
		HttpMethod httpMethod = Objects.requireNonNull(HttpMethod.resolve(method), "method cannot be null.");
		RequestEntity.BodyBuilder builder = RequestEntity.method(httpMethod, URI.create(url));
		if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH) {
			builder.contentType(MediaType.APPLICATION_JSON);
		} else {
			builder.contentType(MediaType.APPLICATION_FORM_URLENCODED);
		}
		ResponseEntity<String> exchange = this.restTemplate.exchange(builder.body(payload), String.class);
		return new APIResponse(exchange.getBody(), exchange.getStatusCodeValue());
	}

	/**
	 *
	 * Do get request for file from input url and stores the file in filepath.
	 * @param url     Absolute URL.
	 * @param filePath the path where the downloaded file is going to be stored.
	 * @return if it succeed, it returns filepath otherwise null or exception.
	 */
	@Override
	protected String doGetRequestForFileAndStore(final String url, final String filePath) throws GeneralException, UnauthorizedException, NotFoundException {
		AtomicInteger status = new AtomicInteger();
		String body = restTemplate.execute(url, HttpMethod.GET, null, clientHttpResponse -> {
			status.set(clientHttpResponse.getRawStatusCode());
			if (clientHttpResponse.getStatusCode().is2xxSuccessful()) {
				File ret = new File(filePath);
				StreamUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(ret));
				return filePath;
			} else {
				return readToEnd(clientHttpResponse.getBody());
			}
		});
		if (status.get() == HttpURLConnection.HTTP_OK) {
			return body;
		}
		handleHttpFailStatuses(status.get(), body);
		return null;
	}

	@Override
	public <P> HttpURLConnection getConnection(String serviceUrl, P body, String requestType) throws IOException {
		throw new IOException("This method should not be called here.");
	}
}
