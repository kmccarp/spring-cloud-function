/*
 * Copyright 2021-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.adapter.aws;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.lambda.runtime.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public final class AWSLambdaUtils {

	private static Log logger = LogFactory.getLog(AWSLambdaUtils.class);

	static final String AWS_API_GATEWAY = "aws-api-gateway";

	static final String AWS_EVENT = "aws-event";

	/**
	 * The name of the headers that stores AWS Context object.
	 */
	public static final String AWS_CONTEXT = "aws-context";

	private AWSLambdaUtils() {

	}

	static boolean isSupportedAWSType(Type inputType) {
		if (FunctionTypeUtils.isMessage(inputType)) {
			inputType = FunctionTypeUtils.getImmediateGenericType(inputType, 0);
		}
		String typeName = inputType.getTypeName();
		return "com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent".equals(typeName)
				|| "com.amazonaws.services.lambda.runtime.events.S3Event".equals(typeName)
				|| "com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent".equals(typeName)
				|| "com.amazonaws.services.lambda.runtime.events.SNSEvent".equals(typeName)
				|| "com.amazonaws.services.lambda.runtime.events.SQSEvent".equals(typeName)
				|| "com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent".equals(typeName)
				|| "com.amazonaws.services.lambda.runtime.events.KinesisEvent".equals(typeName);
	}

	public static Message<byte[]> generateMessage(byte[] payload, Type inputType, boolean isSupplier, JsonMapper jsonMapper) {
		return generateMessage(payload, inputType, isSupplier, jsonMapper, null);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Message<byte[]> generateMessage(byte[] payload, Type inputType, boolean isSupplier, JsonMapper jsonMapper, Context context) {
		if (logger.isInfoEnabled()) {
			logger.info("Received: " + new String(payload, StandardCharsets.UTF_8));
		}

		Object structMessage = jsonMapper.fromJson(payload, Object.class);
		boolean isApiGateway = structMessage instanceof Map
				&& (((Map<String, Object>) structMessage).containsKey("httpMethod") ||
						(((Map<String, Object>) structMessage).containsKey("routeKey") && ((Map) structMessage).containsKey("version")));

		Message<byte[]> requestMessage;
		MessageBuilder<byte[]> builder = MessageBuilder.withPayload(payload);
		if (isApiGateway) {
			builder.setHeader(AWSLambdaUtils.AWS_API_GATEWAY, true);
		}
		if (!isSupplier && AWSLambdaUtils.isSupportedAWSType(inputType)) {
			builder.setHeader(AWSLambdaUtils.AWS_EVENT, true);
		}
		if (context != null) {
			builder.setHeader(AWSLambdaUtils.AWS_CONTEXT, context);
		}
		//
		if (structMessage instanceof Map && ((Map<String, Object>) structMessage).containsKey("headers")) {
			builder.copyHeaders((Map<String, Object>) ((Map<String, Object>) structMessage).get("headers"));
		}
		requestMessage = builder.build();
		return requestMessage;
	}

	private static byte[] extractPayload(Message<Object> msg, JsonMapper objectMapper) {
		if (msg.getPayload() instanceof byte[]) {
			return (byte[]) msg.getPayload();
		}
		else {
			return objectMapper.toJson(msg.getPayload());
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static byte[] generateOutput(Message requestMessage, Message<?> responseMessage,
			JsonMapper objectMapper, Type functionOutputType) {

		Class<?> outputClass = FunctionTypeUtils.getRawType(functionOutputType);
		if (outputClass != null) {
			String outputClassName = outputClass.getName();
			if ("com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse".equals(outputClassName) ||
				"com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent".equals(outputClassName) ||
				"com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent".equals(outputClassName) ||
				"com.amazonaws.services.lambda.runtime.events.IamPolicyResponse".equals(outputClassName)) {
				return extractPayload((Message<Object>) responseMessage, objectMapper);
			}
		}

		byte[] responseBytes = responseMessage  == null ? "\"OK\"".getBytes() : extractPayload((Message<Object>) responseMessage, objectMapper);
		if (requestMessage.getHeaders().containsKey(AWS_API_GATEWAY) && ((boolean) requestMessage.getHeaders().get(AWS_API_GATEWAY))) {
			Map<String, Object> response = new HashMap<>();
			response.put("isBase64Encoded", false);

			AtomicReference<MessageHeaders> headers = new AtomicReference<>();
			int statusCode = HttpStatus.OK.value();
			if (responseMessage != null) {
				headers.set(responseMessage.getHeaders());
				statusCode = headers.get().containsKey("statusCode")
						? (int) headers.get().get("statusCode")
						: HttpStatus.OK.value();
			}

			response.put("statusCode", statusCode);
			if (isRequestKinesis(requestMessage)) {
				HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
				response.put("statusDescription", httpStatus.toString());
			}

			String body = responseMessage == null
					? "\"OK\"" : new String(extractPayload((Message<Object>) responseMessage, objectMapper), StandardCharsets.UTF_8);
			response.put("body", body);

			if (responseMessage != null) {
				Map<String, String> responseHeaders = new HashMap<>();
				headers.get().keySet().forEach(key -> responseHeaders.put(key, headers.get().get(key).toString()));
				response.put("headers", responseHeaders);
			}

			try {
				responseBytes = objectMapper.toJson(response);
			}
			catch (Exception e) {
				throw new IllegalStateException("Failed to serialize AWS Lambda output", e);
			}
		}
		return responseBytes;
	}

	private static boolean isRequestKinesis(Message<Object> requestMessage) {
		return requestMessage.getHeaders().containsKey("Records");
	}
}
