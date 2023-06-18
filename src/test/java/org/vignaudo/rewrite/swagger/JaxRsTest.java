/**
 *     Copyright (C) 2019-2023 Ubiqube.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.vignaudo.rewrite.swagger;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.vignaudo.rewrite.swagger.SwaggerUpgrade3Recipe;

class JaxRsTest implements RewriteTest {
	@Override
	public void defaults(final RecipeSpec spec) {
		spec.recipe(new SwaggerUpgrade3Recipe())
				.parser(JavaParser.fromJavaVersion()
						.logCompilationWarningsAndErrors(true)
						.classpathFromResources(new InMemoryExecutionContext(), "swagger-annotations", "jboss-jaxrs-api"));
	}

	@Test
	void testName() {
		rewriteRun(
				java(
						"""
								package com.yourorg;
								import javax.ws.rs.GET;
								import javax.ws.rs.Path;
								import javax.ws.rs.PathParam;
								import javax.ws.rs.Produces;
								import javax.ws.rs.core.MediaType;
								import javax.ws.rs.core.Response;
								import io.swagger.annotations.Api;
								import io.swagger.annotations.ApiOperation;
								import io.swagger.annotations.ApiParam;
								import io.swagger.annotations.ApiResponse;
								import io.swagger.annotations.ApiResponses;

								class Test {
									@GET
								    @Path("customAssetAttributeValue/id/{deviceId}/attributeName/{attributeName}")
								    @Produces(MediaType.APPLICATION_JSON)
								    @ApiOperation(value = "Get custom asset attribute by device id and attribute name ", notes = "Get custom asset attribute by device id and attribute name")
								    @ApiResponses(value = {
								            @ApiResponse(code = 200, message = "Operation successful", response = Void.class),
								            @ApiResponse(code = 404, message = "The requested data not found.", response = Void.class),
								            @ApiResponse(code = 405, message = "Not allowed method", response = Void.class)
									})
								    public Response getCustomAssetAttribute(@ApiParam(name = "deviceId", value = "Id of device. Example = 3456", example = "3456", type = "Long", required = true) @PathParam("deviceId") long deviceId,
								                                            @ApiParam(name = "attributeName", value = "Name of attribute.", type = "String",required = true) @PathParam("attributeName") String attributeName) throws ServiceException {
								                            }
								}
																								""",
						"""
								package com.yourorg;
								}
								"""));
	}

}
