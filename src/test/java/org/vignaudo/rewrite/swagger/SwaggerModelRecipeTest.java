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

class SwaggerModelRecipeTest implements RewriteTest {

	@Override
	public void defaults(final RecipeSpec spec) {
		spec.recipe(new SwaggerModelRecipe())
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
										import io.swagger.annotations.ApiModel;
										import io.swagger.annotations.ApiModelProperty;

										@ApiModel(description = "Model for input")
										public class RefData {
											private String id = null;
											@ApiModelProperty(required = true, value = "Identifier of this \\"Individual subscription\\" resource. ")
											public String getId() {
												return id;
											}
										}
								""",
						"""
								package com.yourorg;
								import io.swagger.v3.oas.annotations.media.Schema;

								@Schema(description = "Model for input")
								public class RefData {
									private String id = null;
									@Schema(required = true, value = "Identifier of this \\"Individual subscription\\" resource. ")
									public String getId() {
										return id;
									}
								}

								"""));
	}

}
