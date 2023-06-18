package org.vignaudo.rewrite.swagger;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.vignaudo.rewrite.swagger.SwaggerUpgrade3Recipe;

/**
 * Unit test for simple App.
 */
class AppTest implements RewriteTest {
	@Override
	public void defaults(final RecipeSpec spec) {
		spec.recipe(new SwaggerUpgrade3Recipe())
				.parser(JavaParser.fromJavaVersion()
						.logCompilationWarningsAndErrors(true)
						.classpathFromResources(new InMemoryExecutionContext(), "swagger-annotations"));
	}

	@Test
	void testName() {
		rewriteRun(
				java(
						"""
								package com.yourorg;
								import java.util.Map;
								import jakarta.validation.Valid;
								import io.swagger.annotations.ApiOperation;
								import io.swagger.annotations.ApiParam;

								public interface NsDescriptors281Sol005Api {
								    @ApiOperation(value = "", nickname = "nsDescriptorsGet", notes = "The GET method queries information about multiple NS descriptor resources. ", response = Void.class, responseContainer = "List", tags = {})
								    void nsDescriptorsGet(@ApiParam(value = "All query parameters. ", required = true) @Valid Map<String, String> requestParams,
								            @ApiParam(value = "Marker to obtain the next page of a paged response. Shall be supported by the NFVO if the NFVO supports alternative 2 (paging) according to clause 5.4.2.1 of ETSI GS NFV-SOL 013 for this resource. ") String nextpageOpaqueMarker);
								}
																								""",
						"""
								package com.yourorg;
								import java.util.Map;
								import jakarta.validation.Valid;
								import io.swagger.v3.oas.annotations.Operation;
								import io.swagger.v3.oas.annotations.Parameter;
								import io.swagger.v3.oas.annotations.enums.ParameterIn;

								public interface NsDescriptors281Sol005Api {
								    @Operation(description = "The GET method queries information about multiple NS descriptor resources. ")
								    void nsDescriptorsGet(@Parameter(in = ParameterIn.DEFAULT, description = "All query parameters. ", required = true) @Valid Map<String, String> requestParams,
								            @Parameter(in = ParameterIn.DEFAULT, description = "Marker to obtain the next page of a paged response. Shall be supported by the NFVO if the NFVO supports alternative 2 (paging) according to clause 5.4.2.1 of ETSI GS NFV-SOL 013 for this resource. ") String nextpageOpaqueMarker);
								}
								"""));
	}

}
