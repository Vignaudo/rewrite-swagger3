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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindImports;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.Assignment;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.ShallowClass;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 *
 * @author Olivier Vignaud
 *
 */
public class Sw3ResponseRecipe extends Recipe {
	private static final Logger LOG = LoggerFactory.getLogger(Sw3ResponseRecipe.class);

	@Override
	public String getDisplayName() {
		return "Replace swagger2 annotations by swagger3";
	}

	@Override
	public String getDescription() {
		return "Replace swagger2 annotations by swagger3.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return Preconditions.check(Preconditions.or(
				new UsesType<>("io.swagger.annotations.ApiResponses", false),
				new FindImports("io.swagger.annotations.ApiResponses", false).getVisitor()), new ApiResponseVisitor());
	}

	public static class ApiResponseVisitor extends JavaIsoVisitor<ExecutionContext> {
		private static final AnnotationMatcher ANN_API_RESPONSES = new AnnotationMatcher("@io.swagger.annotations.ApiResponses");
		private static final AnnotationMatcher ANN = new AnnotationMatcher("@io.swagger.annotations.Api");
		private static final ShallowClass SW2_API_RESPONSE = JavaType.ShallowClass.build("io.swagger.annotations.ApiResponse");
		private static final ShallowClass SW2_API_RESPONSES = JavaType.ShallowClass.build("io.swagger.annotations.ApiResponses");

		@Override
		public ClassDeclaration visitClassDeclaration(final ClassDeclaration classDecl, final ExecutionContext p) {
			final ClassDeclaration c = super.visitClassDeclaration(classDecl, p);
			return c.withLeadingAnnotations(handleClassAnnotations(c.getLeadingAnnotations()));
		}

		private List<Annotation> handleClassAnnotations(final List<Annotation> leadingAnnotations) {
			final List<J.Annotation> ret = new ArrayList<>();
			for (final Annotation annotation : leadingAnnotations) {
				if (ANN.matches(annotation)) {
					final Annotation res = convertToTag(annotation);
					ret.add(res);
					maybeRemoveImport("io.swagger.annotations.Api");
					maybeRemoveImport("io.swagger.annotations.Authorization");
					maybeAddImport(Tag.class.getCanonicalName());
				} else {
					ret.add(annotation);
				}
			}
			return ret;
		}

		private static Annotation convertToTag(final Annotation annotation) {
			final List<Expression> args = filterArguments(annotation.getArguments(), "value");
			if (args.isEmpty()) {
				return null;
			}
			final Map<String, J.Assignment> map = new LinkedHashMap<>();
			map.put("name", (J.Assignment) args.get(0));
			Optional.ofNullable(filterArguments(annotation.getArguments(), "description"))
					.filter(x -> !x.isEmpty())
					.ifPresent(x -> map.put("description", (J.Assignment) args.get(0)));
			return createAnnotation(Tag.class, map);
		}

		private static @Nullable List<Expression> filterArguments(@Nullable final List<Expression> arguments, final String element) {
			return arguments.stream().filter(Assignment.class::isInstance)
					.map(J.Assignment.class::cast)
					.filter(x -> ((J.Identifier) x.getVariable()).getSimpleName().equals(element))
					.map(Expression.class::cast)
					.toList();
		}

		@Override
		public MethodDeclaration visitMethodDeclaration(final MethodDeclaration method, final ExecutionContext p) {
			final MethodDeclaration m = super.visitMethodDeclaration(method, p);
			final MethodDeclaration m1 = m.withLeadingAnnotations(convertApiResponses(m.getLeadingAnnotations()));
			return maybeAutoFormat(m, m1, p);
		}

		private List<Annotation> convertApiResponses(final List<Annotation> leadingAnnotations) {
			return leadingAnnotations.stream()
					.map(this::convertAnnotation)
					.toList();
		}

		private Annotation convertAnnotation(final Annotation annotation) {
			if (!ANN_API_RESPONSES.matches(annotation)) {
				return annotation;
			}
			final Expression args = annotation.getArguments().iterator().next();
			if (args instanceof final J.Assignment ass &&
					"value".equals(((J.Identifier) ass.getVariable()).getSimpleName())) {
				final Expression subAss = ass.getAssignment();
				if (subAss instanceof final J.NewArray na) {
					final List<Expression> init = na.getInitializer();
					final List<JRightPadded<Expression>> resp = init.stream()
							.map(x -> convertApiResponse((Annotation) x, init))
							.map(ApiResponseVisitor::packJRight)
							.toList();
					final ShallowClass tagTypeResponse = JavaType.ShallowClass.build("io.swagger.v3.oas.annotations.responses.ApiResponses");
					final JContainer<Expression> jContainer = JContainer.build(Space.EMPTY, resp, Markers.EMPTY);
					final J.NewArray jArray = new J.NewArray(randomId(), Space.EMPTY, Markers.EMPTY, null, List.of(), jContainer, tagTypeResponse);
					final ShallowClass tagType = JavaType.ShallowClass.build("io.swagger.v3.oas.annotations.responses.ApiResponses");
					maybeRemoveImport(SW2_API_RESPONSES);
					maybeAddImport(tagType);
					final NameTree annType = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, tagType.getClassName(), tagType, null);
					final Expression value = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, "value", null, null);
					final J.Assignment assigment = new J.Assignment(randomId(), Space.EMPTY, Markers.EMPTY, value, JLeftPadded.build(jArray), tagType);
					final JContainer<Expression> jContainerRoot = JContainer.build(List.of(JRightPadded.build(assigment)));
					return new Annotation(randomId(), Space.EMPTY, Markers.EMPTY, annType, jContainerRoot);
				}
			}
			throw new IllegalArgumentException("");
		}

		private J.Annotation convertApiResponse(final J.Annotation ann, final List<Expression> init) {
			@Nullable
			final List<Expression> args = ann.getArguments();
			final List<JRightPadded<Expression>> paddedAargs = args.stream()
					.map(x -> convertAssigment((Assignment) x, init))
					.map(ApiResponseVisitor::packJRight)
					.toList();
			@Nullable
			final JContainer<Expression> jContainer = JContainer.build(Space.EMPTY, paddedAargs, Markers.EMPTY);
			final ShallowClass tagType = JavaType.ShallowClass.build("io.swagger.v3.oas.annotations.responses.ApiResponse");
			maybeRemoveImport(SW2_API_RESPONSE);
			maybeAddImport(tagType);
			final NameTree annType = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, tagType.getClassName(), tagType, null);
			return new Annotation(randomId(), Space.EMPTY, Markers.EMPTY, annType, jContainer);
		}

		private static JRightPadded<Expression> packJRight(final Expression x) {
			return JRightPadded.build(x);
		}

		private J.Assignment convertAssigment(final J.Assignment x, final List<Expression> init) {
			final String name = ((J.Identifier) x.getVariable()).getSimpleName();
			if ("code".equals(name)) {
				return codeConvertion(x);
			}
			if ("message".equals(name)) {
				return sameConvertion("description", x);
			}
			if ("response".equals(name)) {
				maybeAddImport(Content.class.getCanonicalName());
				final Assignment schema = createSchema(x, init);
				final Annotation content = createAnnotation(Content.class, Map.of("schema", schema));
				return createAssignment("content", content);
			}
			if ("responseContainer".equals(name)) {
				// Remove it.
				return null;
			}
			throw new IllegalArgumentException("Unknown assignment " + name);
		}

		private J.Assignment createSchema(final J.Assignment x, final List<Expression> init) {
			final Optional<Assignment> optContainer = findAttribute(init, "responseContainer");
			maybeAddImport(Schema.class.getCanonicalName());
			if (optContainer.isEmpty()) {
				final Annotation ann = createAnnotation(Schema.class, Map.of("implementation", x));
				return createAssignment("schema", ann);
			}
			final Annotation annSchema = createAnnotation(Schema.class, Map.of("implementation", x));
			final Annotation ann = createAnnotation2(ArraySchema.class, Map.of("schema", annSchema));
			maybeAddImport(ArraySchema.class.getCanonicalName());
			return createAssignment("array", ann);
		}

		private static Optional<J.Assignment> findAttribute(final List<Expression> init, final String string) {
			return init.stream()
					.filter(Assignment.class::isInstance)
					.map(J.Assignment.class::cast)
					.filter(x -> ((J.Identifier) x.getVariable()).getSimpleName().equals(string))
					.findFirst();
		}

		private static Annotation createAnnotation2(final Class<?> class1, final Map<String, Annotation> of) {
			final List<JRightPadded<Expression>> paddedAargs = of.entrySet().stream()
					.map(c -> createAssignment(c.getKey(), c.getValue()))
					.map(ApiResponseVisitor::packJRight)
					.toList();
			final ShallowClass clazz = JavaType.ShallowClass.build(class1.getCanonicalName());
			final JContainer<Expression> jContainer = JContainer.build(Space.EMPTY, paddedAargs, Markers.EMPTY);
			final NameTree annType = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, clazz.getClassName(), clazz, null);
			return new Annotation(randomId(), Space.EMPTY, Markers.EMPTY, annType, jContainer);
		}

		private static J.Annotation createAnnotation(final Class<?> class1, final Map<String, Assignment> of) {
			final List<JRightPadded<Expression>> paddedAargs = of.entrySet().stream()
					.map(c -> createAssignment(c.getKey(), c.getValue().getAssignment()))
					.map(ApiResponseVisitor::packJRight)
					.toList();
			final ShallowClass clazz = JavaType.ShallowClass.build(class1.getCanonicalName());
			final JContainer<Expression> jContainer = JContainer.build(Space.EMPTY, paddedAargs, Markers.EMPTY);
			final NameTree annType = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, clazz.getClassName(), clazz, null);
			return new Annotation(randomId(), Space.EMPTY, Markers.EMPTY, annType, jContainer);
		}

		private static Assignment sameConvertion(final String string, final Assignment x) {
			final J.Literal lit = (J.Literal) x.getAssignment();
			return createAssignment(string, lit);
		}

		private static Assignment codeConvertion(final Assignment x) {
			final J.Literal ass = (J.Literal) x.getAssignment();
			final J.Literal lit = new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, ass.getValue(), "\"" + ass.getValueSource() + "\"", null, JavaType.Primitive.String);
			return createAssignment("responseCode", lit);
		}

		private static Assignment createAssignment(final String attr, final Expression lit) {
			final J.Identifier variable = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, attr, null, null);
			final JLeftPadded<Expression> padded = new JLeftPadded<>(Space.EMPTY, lit, Markers.EMPTY);
			return new J.Assignment(randomId(), Space.EMPTY, Markers.EMPTY, variable, padded, JavaType.Primitive.String);
		}
	}
}
