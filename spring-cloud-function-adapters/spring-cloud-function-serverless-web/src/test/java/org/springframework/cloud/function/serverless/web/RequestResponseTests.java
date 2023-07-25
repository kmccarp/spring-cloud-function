/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.cloud.function.serverless.web;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import org.springframework.cloud.function.test.app.Pet;
import org.springframework.cloud.function.test.app.PetStoreSpringAppConfig;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Oleg Zhurakousky
 *
 */
public class RequestResponseTests {

    private final ObjectMapper mapper = new ObjectMapper();

	private ProxyMvc mvc;

	@BeforeEach
	public void before() {
		this.mvc = ProxyMvc.INSTANCE(ProxyErrorController.class, PetStoreSpringAppConfig.class);
	}

	@AfterEach
	public void after() {
		this.mvc.stop();
	}

	@Test
	public void validateAccessDeniedWithCustomHandler() throws Exception {
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/foo");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		assertThat(response.getErrorMessage()).isEqualTo("Can't touch this");
		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	public void validateGetListOfPojos() throws Exception {
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/pets");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		TypeReference<List<Pet>> tr = new TypeReference<>() {
		};
		List<Pet> pets = mapper.readValue(response.getContentAsByteArray(), tr);
		assertThat(pets.size()).isEqualTo(10);
		assertThat(pets.get(0)).isInstanceOf(Pet.class);
	}

	@Test
	public void validateGetListOfPojosWithParam() throws Exception {
		ProxyHttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/pets");
		request.setParameter("limit", "5");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		TypeReference<List<Pet>> tr = new TypeReference<>() {
		};
		List<Pet> pets = mapper.readValue(response.getContentAsByteArray(), tr);
		assertThat(pets.size()).isEqualTo(5);
		assertThat(pets.get(0)).isInstanceOf(Pet.class);
	}

	@WithMockUser("spring")
	@Test
	public void validateGetPojo() throws Exception {
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/pets/6e3cc370-892f-4efe-a9eb-82926ff8cc5b");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

	@Test
	public void errorThrownFromMethod() throws Exception {
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/pets/2");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(response.getErrorMessage()).isEqualTo("No such Dog");
	}

	@Test
	public void errorUnexpectedWhitelabel() throws Exception {
		HttpServletRequest request = new ProxyHttpServletRequest(null, "GET", "/pets/2/3/4");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
	}

	@Test
	public void validatePostWithBody() throws Exception {
		ProxyHttpServletRequest request = new ProxyHttpServletRequest(null, "POST", "/pets/");
		String jsonPet = "{\n"
				+ "   \"id\":\"1234\",\n"
				+ "   \"breed\":\"Canish\",\n"
				+ "   \"name\":\"Foo\",\n"
				+ "   \"date\":\"2012-04-23T18:25:43.511Z\"\n"
				+ "}";
		request.setContent(jsonPet.getBytes());
		request.setContentType("application/json");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

	@Test
	public void validatePostAsyncWithBody() throws Exception {
		ProxyHttpServletRequest request = new ProxyHttpServletRequest(null, "POST", "/petsAsync/");
		String jsonPet = "{\n"
				+ "   \"id\":\"1234\",\n"
				+ "   \"breed\":\"Canish\",\n"
				+ "   \"name\":\"Foo\",\n"
				+ "   \"date\":\"2012-04-23T18:25:43.511Z\"\n"
				+ "}";
		request.setContent(jsonPet.getBytes());
		request.setContentType("application/json");
		ProxyHttpServletResponse response = new ProxyHttpServletResponse();
		mvc.service(request, response);
		Pet pet = mapper.readValue(response.getContentAsByteArray(), Pet.class);
		assertThat(pet).isNotNull();
		assertThat(pet.getName()).isNotEmpty();
	}

}
