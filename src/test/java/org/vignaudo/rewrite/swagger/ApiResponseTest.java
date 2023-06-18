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
import org.vignaudo.rewrite.swagger.Sw3ResponseRecipe;

class ApiResponseTest implements RewriteTest {
	@Override
	public void defaults(final RecipeSpec spec) {
		spec.recipe(new Sw3ResponseRecipe())
				.parser(JavaParser.fromJavaVersion()
						.logCompilationWarningsAndErrors(true)
						.classpathFromResources(new InMemoryExecutionContext(), "swagger-annotations"));
	}

	@Test
	void testName() throws Exception {
		rewriteRun(
				java(
						"""
								package com.yourorg;
								import java.util.Map;
								import io.swagger.annotations.Api;
								import io.swagger.annotations.ApiParam;
								import io.swagger.annotations.ApiResponse;
								import io.swagger.annotations.ApiResponses;

								@Api(value = "pm_jobs", description = "the pm_jobs API")
								public interface NsDescriptors281Sol005Api {
									@ApiResponses(value = {
										@ApiResponse(code = 200, message = "200 OK Shall be returned when information about zero or more alarms has been queried successfully. The response body shall contain in an array the representations of zero or more alarms as defined in clause 7.5.2.4. If the \\"filter\\" URI parameter was supplied in the request, the data in the response body shall have been transformed according to the rules specified in clause 5.2.2 of ETSI GS NFV-SOL 013. If the VNFM supports alternative 2 (paging) according to clause 5.4.2.1 of ETSI GS NFV-SOL 013 for this resource, inclusion of the Link HTTP header in this response shall follow the provisions in clause 5.4.2.3 of ETSI GS NFV-SOL 013. ", response = Void.class),
										@ApiResponse(code = 504, message = "504 GATEWAY TIMEOUT If the API producer encounters a timeout while waiting for a response from an upstream server (i.e. a server that the API producer communicates with when fulfilling a request), it should respond with this response code. ", response = Void.class) })
									void alarmsGet(
									@ApiParam(value = "All query parameters. ", required = true) Map<String, String> requestParams,
									@ApiParam(value = "Marker to obtain the next page of a paged response. Shall be supported by the  VNFM if the VNFM supports alternative 2 (paging) according to clause 5.4.2.1 of ETSI GS NFV-SOL 013 for this resource. ") String nextpageOpaqueMarker);
								}
								""",
						"""
								package com.yourorg;
								import java.util.Map;
								import io.swagger.annotations.ApiParam;
								import io.swagger.v3.oas.annotations.responses.ApiResponse;
								import io.swagger.v3.oas.annotations.responses.ApiResponses;
								public interface NsDescriptors281Sol005Api {
									@ApiResponses(value = {
										@ApiResponse(code = "200", description = "200 OK Shall be returned when information about zero or more alarms has been queried successfully. The response body shall contain in an array the representations of zero or more alarms as defined in clause 7.5.2.4. If the \\"filter\\" URI parameter was supplied in the request, the data in the response body shall have been transformed according to the rules specified in clause 5.2.2 of ETSI GS NFV-SOL 013. If the VNFM supports alternative 2 (paging) according to clause 5.4.2.1 of ETSI GS NFV-SOL 013 for this resource, inclusion of the Link HTTP header in this response shall follow the provisions in clause 5.4.2.3 of ETSI GS NFV-SOL 013. ", schema=@Schema(implementation= Void.class)),
										@ApiResponse(code = "504", description = "504 GATEWAY TIMEOUT If the API producer encounters a timeout while waiting for a response from an upstream server (i.e. a server that the API producer communicates with when fulfilling a request), it should respond with this response code. ", schema=@Schema(implementation= Void.class)) })
									void alarmsGet(
									@ApiParam(value = "All query parameters. ", required = true) Map<String, String> requestParams,
									@ApiParam(value = "Marker to obtain the next page of a paged response. Shall be supported by the  VNFM if the VNFM supports alternative 2 (paging) according to clause 5.4.2.1 of ETSI GS NFV-SOL 013 for this resource. ") String nextpageOpaqueMarker);
								}
								"""));

	}
}
