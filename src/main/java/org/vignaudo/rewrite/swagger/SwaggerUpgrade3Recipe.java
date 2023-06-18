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

import static org.openrewrite.Tree.randomId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindImports;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.Assignment;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.JavaType.ShallowClass;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

public class SwaggerUpgrade3Recipe extends Recipe {
	private static final Logger LOG = LoggerFactory.getLogger(SwaggerUpgrade3Recipe.class);
	private static final AnnotationMatcher ANN_API_OPERATION = new AnnotationMatcher("@io.swagger.annotations.ApiOperation");
	private static final AnnotationMatcher ANN_API_PARAM = new AnnotationMatcher("@io.swagger.annotations.ApiParam");

	private static final AnnotationMatcher ANN_SPRING_REQUEST_PARAM = new AnnotationMatcher("org.springframework.web.bind.annotation.RequestParam");
	private static final AnnotationMatcher ANN_SPRING_HEADER_PARAM = new AnnotationMatcher("org.springframework.web.bind.annotation.RequestHeader");
	private static final AnnotationMatcher ANN_SPRING_PATH_PARAM = new AnnotationMatcher("org.springframework.web.bind.annotation.PathVariable");
	private static final AnnotationMatcher ANN_SPRING_COOKIE_PARAM = new AnnotationMatcher("org.springframework.web.bind.annotation.CookieValue");
	// JAX-rs
	private static final AnnotationMatcher ANN_JAXRS_PATH_PARAM = new AnnotationMatcher("javax.ws.rs.PathParam");
	private static final AnnotationMatcher ANN_JAXRS_QUERY_PARAM = new AnnotationMatcher("javax.ws.rs.QueryParam");

	@Override
	public String getDisplayName() {
		return "swagger-upgrade";
	}

	@Override
	public String getDescription() {
		return "descr.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return Preconditions.check(Preconditions.or(
				new UsesType<>("io.swagger.annotations.ApiParam", false),
				new UsesType<>("io.swagger.annotations.ApiOperation", false),
				new FindImports("io.swagger.annotations.ApiOperation").getVisitor(),
				new FindImports("io.swagger.annotations.ApiParam").getVisitor()), new SwaggerVisitor());
	}

	private class SwaggerVisitor extends JavaIsoVisitor<ExecutionContext> {

		@Nullable
		private JavaParser.Builder<?, ?> javaParser;

		private JavaParser.Builder<?, ?> javaParser(final ExecutionContext ctx) {
			if (javaParser == null) {
				javaParser = JavaParser.fromJavaVersion()
						.classpathFromResources(ctx, "junit-jupiter-api-5.9");
			}
			return javaParser;
		}

		@Override
		public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl, final ExecutionContext ctx) {
			return super.visitClassDeclaration(classDecl, ctx);
		}

		@Override
		public J.MethodDeclaration visitMethodDeclaration(final J.MethodDeclaration method, final ExecutionContext ctx) {
			J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
			for (final J.Annotation annotation : m.getLeadingAnnotations()) {
				if (ANN_API_OPERATION.matches(annotation)) {
					final List<Expression> args = annotation.getArguments();
					final Expression note = findNotesOrValue(args);
					doAfterVisit(new RemoveAnnotationVisitor(ANN_API_OPERATION));
					m = JavaTemplate.builder("@Operation(description = #{any(string)})")
							.javaParser(javaParser(ctx))
							.imports("io.swagger.v3.oas.annotations.Operation")
							.build()
							.apply(
									updateCursor(m),
									m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)),
									note);
					maybeAddImport("io.swagger.v3.oas.annotations.Operation", false);
					maybeRemoveImport("io.swagger.annotations.ApiOperation");
				}
			}
			return m.withParameters(convertParameters(m.getParameters()));
		}

		private Expression findNotesOrValue(final List<Expression> args) {
			final Expression ret = findAnnotation(args, "notes");
			if ((ret != null) && !isEmpty(ret)) {
				return ret;
			}
			return findAnnotation(args, "value");
		}

		private boolean isEmpty(final Expression ret) {
			if (!(ret instanceof final J.Literal lit)) {
				return false;
			}
			@Nullable
			final Object val = lit.getValue();
			if (val instanceof final String str) {
				return str.isEmpty();
			}
			return false;
		}

		private Expression findAnnotation(final List<Expression> args, final String element) {
			return args.stream()
					.filter(x -> x instanceof J.Assignment)
					.map(J.Assignment.class::cast)
					.filter(x -> isMatching(x.getVariable(), element))
					.map(x -> x.getAssignment())
					.findFirst().orElse(null);
		}

		private List<Statement> convertParameters(final List<Statement> parameters) {
			final List ret = new ArrayList<>();
			for (final Statement stmt : parameters) {
				if (stmt instanceof final J.VariableDeclarations vd) {
					ret.add(vd.withLeadingAnnotations(convertLeadingAnnotations(vd.getAllAnnotations())));
				} else if (stmt instanceof J.Empty) {
					ret.add(stmt);
				} else {
					throw new IllegalArgumentException("vd is " + stmt.getClass());
				}
			}
			maybeRemoveImport("io.swagger.annotations.ApiParam");
			return ret;
		}

		private List<Annotation> convertLeadingAnnotations(final List<Annotation> allAnnotations) {
			final Optional<Annotation> optSwAnn = findSwagger2Annotation(allAnnotations);
			if (optSwAnn.isEmpty()) {
				return allAnnotations;
			}
			final ParameterIn param = convert(allAnnotations);
			final J.Annotation sw3Ann = createSwagger3Annotation(optSwAnn.get(), param);
			final List<Annotation> remaind = allAnnotations.stream()
					.filter(x -> !ANN_API_PARAM.matches(x))
					.toList();
			final List<Annotation> ret = new ArrayList<>();
			ret.add(sw3Ann);
			ret.addAll(remaind);
			return ret;
		}

		private Annotation createSwagger3Annotation(final Annotation annotation, final ParameterIn param) {
			final FullyQualified tagType = ShallowClass.build("io.swagger.v3.oas.annotations.Parameter");
			maybeAddImport(tagType);
			maybeAddImport(ParameterIn.class.getCanonicalName(), false);
			final List params = new ArrayList<>();
			params.add(createAnnotationAssigment("in", param));
			params.addAll(mapParams(annotation.getArguments()));
			return new J.Annotation(
					randomId(),
					Space.EMPTY,
					Markers.EMPTY,
					new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Parameter.class.getSimpleName(), tagType, null),
					JContainer.build(Space.EMPTY, params, Markers.EMPTY));
		}

		private <T> Collection<JRightPadded<T>> mapParams(@Nullable final List<Expression> arguments) {
			final List<JRightPadded<T>> ret = new ArrayList<>();
			for (final Expression expression : arguments) {
				if (!(expression instanceof final J.Assignment ass)) {
					throw new IllegalArgumentException("Unknown " + expression.getClass());
				}
				final Optional<J.Assignment> optAss = buildAssignement(ass);
				if (optAss.isPresent()) {
					ret.add(new JRightPadded<>((T) optAss.get(), Space.SINGLE_SPACE, Markers.EMPTY));
				}
			}
			return ret;
		}

		private Optional<Assignment> buildAssignement(final Assignment ass) {
			final String name = ((J.Identifier) ass.getVariable()).getSimpleName();
			final String attr = attributeToSw3(name);
			if (null == attr) {
				return Optional.empty();
			}
			final Expression tre = ass.getAssignment();
			if (tre instanceof final J.Literal lit) {
				final Assignment opt = createAssignment(attr, lit);
				return Optional.of(opt);
			}
			if (tre instanceof final J.Binary bin) {
				final Assignment opt = createAssignment(attr, bin);
				return Optional.of(opt);
			}
			throw new IllegalArgumentException("Unknown type: " + tre.getClass());
		}

		private Assignment createAssignment(final String attr, final Expression lit) {
			final J.Identifier variable = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, attr, null, null);
			final JLeftPadded<Expression> padded = new JLeftPadded<>(Space.SINGLE_SPACE, lit, Markers.EMPTY);
			return new J.Assignment(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, variable, padded, JavaType.Primitive.String);
		}

		private String attributeToSw3(final String name) {
			return switch (name) {
			case "required" -> "required";
			case "value" -> "description";
			case "name" -> "name";
			case "example" -> "example";
			case "type" -> null;
			case "allowableValues" -> null;
			case "defaultValue" -> null;
			default -> {
				throw new IllegalArgumentException("Unknown attribute: " + name);
			}
			};
		}

		private JRightPadded<Expression> createAnnotationAssigment(final String string, final ParameterIn param) {
			final J.Identifier variable = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, string, null, null);
			final JLeftPadded<Expression> assigment = new JLeftPadded<>(Space.SINGLE_SPACE,
					new J.Literal(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, param, "ParameterIn." + param.name(), null, null), Markers.EMPTY);
			final JavaType javaType = ShallowClass.build(param.getClass().getCanonicalName());
			maybeAddImport(param.getClass().getCanonicalName());
			return new JRightPadded<>(
					new J.Assignment(randomId(), Space.EMPTY, Markers.EMPTY, variable, assigment, javaType), Space.EMPTY, Markers.EMPTY);
		}

		private Optional<Annotation> findSwagger2Annotation(final List<Annotation> allAnnotations) {
			return allAnnotations.stream()
					.filter(ANN_API_PARAM::matches)
					.findFirst();
		}

		private boolean isMatching(final Expression expression, final String arg) {
			if (expression instanceof final J.Identifier i) {
				return i.getSimpleName().equals(arg);
			}
			return false;
		}

		private static final ParameterIn convert(final List<J.Annotation> annotations) {
			if (match(annotations, ANN_SPRING_COOKIE_PARAM)) {
				return ParameterIn.COOKIE;
			}
			if (match(annotations, ANN_SPRING_HEADER_PARAM)) {
				return ParameterIn.HEADER;
			}
			if (match(annotations, ANN_SPRING_PATH_PARAM)) {
				return ParameterIn.PATH;
			}
			if (match(annotations, ANN_SPRING_REQUEST_PARAM)) {
				return ParameterIn.QUERY;
			}
			// Jax-rs
			if (match(annotations, ANN_JAXRS_PATH_PARAM)) {
				return ParameterIn.PATH;
			}
			if (match(annotations, ANN_JAXRS_QUERY_PARAM)) {
				return ParameterIn.QUERY;
			}
			LOG.warn("Unable to find annotations in {}", annotations);
			return ParameterIn.DEFAULT;
		}

		private static final boolean match(final List<J.Annotation> annotations, final AnnotationMatcher match) {
			return annotations.stream()
					.anyMatch(match::matches);
		}
	}
}

record LocalAssignement(String name, Expression expr) {
	//
}