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

import java.util.Comparator;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class SwaggerVisitor extends JavaIsoVisitor<ExecutionContext> {
	private static final AnnotationMatcher ANN_API = new AnnotationMatcher("@oio.swagger.annotations.Api");
	private static final AnnotationMatcher ANN_API_OPERATION = new AnnotationMatcher("@io.swagger.annotations.ApiOperation");
	private static final AnnotationMatcher ANN_API_PARAM = new AnnotationMatcher("@oio.swagger.annotations.ApiParam");
	private static final AnnotationMatcher ANN_API_RESPONSE = new AnnotationMatcher("@oio.swagger.annotations.ApiResponse");
	private static final AnnotationMatcher ANN_API_RESPONSES = new AnnotationMatcher("@oio.swagger.annotations.ApiResponses");

	@Nullable
	private JavaParser.Builder<?, ?> javaParser;

	private JavaParser.Builder<?, ?> javaParser(final ExecutionContext ctx) {
		if (javaParser == null) {
			javaParser = JavaParser.fromJavaVersion()
					.classpathFromResources(ctx, "junit-jupiter-api-5.9", "apiguardian-api-1.1");
		}
		return javaParser;
	}

	// @Operation(summary = "", description = "The GET method queries information
	// about multiple NS instances. See clause 6.4.2.3.2. ", tags = {})
	// @ApiOperation(value = "", nickname = "nsDescriptorsGet", notes = "The GET
	// method queries information about multiple NS descriptor resources. ",
	// response = Void.class, responseContainer = "List", tags = {})
	// value = summary
	// notes = description
	@Override
	public J.MethodDeclaration visitMethodDeclaration(final J.MethodDeclaration method, final ExecutionContext ctx) {
		J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
		for (final J.Annotation annotation : m.getLeadingAnnotations()) {
			if (ANN_API_OPERATION.matches(annotation)) {
				final List<Expression> args = annotation.getArguments();
				doAfterVisit(new RemoveAnnotationVisitor(ANN_API_OPERATION));
				m = JavaTemplate.builder("@Operation(description = #{any(string)})")
						.javaParser(javaParser(ctx))
						.imports("org.junit.jupiter.api.Timeout")
						.build()
						.apply(
								updateCursor(m),
								m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)),
								args.get(0));
			}
		}
		// @Parameter(in = ParameterIn.QUERY, description = "Attribute-based filtering.
		// ", schema = @Schema()))
		return m;
	}
}
