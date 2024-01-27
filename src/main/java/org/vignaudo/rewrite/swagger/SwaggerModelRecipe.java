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

import java.util.List;
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

public class SwaggerModelRecipe extends Recipe {
	private static final Logger LOG = LoggerFactory.getLogger(SwaggerModelRecipe.class);

	@Override
	public String getDisplayName() {
		return "Swagger 2 to swagger model v3";
	}

	@Override
	public String getDescription() {
		return "Swagger 2 to swagger model v3.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return Preconditions.check(Preconditions.or(
				new UsesType<>("io.swagger.annotations.ApiModel", false),
				new FindImports("io.swagger.annotations.ApiModel", false).getVisitor()), new SwaggerModelVisitor());
	}

	private static class SwaggerModelVisitor extends JavaIsoVisitor<ExecutionContext> {
		private static final AnnotationMatcher ANN_API_MODEL = new AnnotationMatcher("@io.swagger.annotations.ApiModel");
		private static final AnnotationMatcher ANN_API_MODEL_PROPERTY = new AnnotationMatcher("@io.swagger.annotations.ApiModelProperty");
		private static final ShallowClass SW2_API_MODEL = JavaType.ShallowClass.build("io.swagger.annotations.ApiModel");
		private static final ShallowClass SW2_API_MODEL_PROPERTY = JavaType.ShallowClass.build("io.swagger.annotations.ApiModelProperty");

		@Override
		public Annotation visitAnnotation(final Annotation annotation, final ExecutionContext p) {
			final Annotation a = super.visitAnnotation(annotation, p);
			if (ANN_API_MODEL.matches(a)) {
				return convertApiResponse(a);
			}
			if (ANN_API_MODEL_PROPERTY.matches(a)) {
				return convertModelProperty(a);
			}
			maybeRemoveImport(SW2_API_MODEL_PROPERTY);
			maybeRemoveImport(SW2_API_MODEL);
			return a;
		}

		private Annotation convertModelProperty(final Annotation ann) {
			final List<Expression> args = ann.getArguments();
			final List<JRightPadded<Expression>> paddedAargs = args.stream()
					.map(x -> convertAssigmentModel((Assignment) x))
					.map(SwaggerModelVisitor::packJRight)
					.toList();
			@Nullable
			final JContainer<Expression> jContainer = JContainer.build(Space.EMPTY, paddedAargs, Markers.EMPTY);
			final ShallowClass tagType = JavaType.ShallowClass.build("io.swagger.v3.oas.annotations.media.Schema");
			maybeRemoveImport(SW2_API_MODEL_PROPERTY);
			maybeAddImport(tagType);
			final NameTree annType = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, tagType.getClassName(), tagType, null);
			return new Annotation(randomId(), Space.EMPTY, Markers.EMPTY, annType, jContainer);
		}

		private J.Annotation convertApiResponse(final J.Annotation ann) {
			@Nullable
			final List<Expression> args = ann.getArguments();
			final List<JRightPadded<Expression>> paddedAargs = Optional.ofNullable(args).map(x -> x.stream()
					.map(y -> convertAssigment((Assignment) y))
					.map(SwaggerModelVisitor::packJRight)
					.toList()).orElseGet(List::of);
			@Nullable
			final JContainer<Expression> jContainer = JContainer.build(Space.SINGLE_SPACE, paddedAargs, Markers.EMPTY);
			final ShallowClass tagType = JavaType.ShallowClass.build("io.swagger.v3.oas.annotations.media.Schema");
			maybeRemoveImport(SW2_API_MODEL);
			maybeAddImport(tagType);
			final NameTree annType = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, tagType.getClassName(), tagType, null);
			return new Annotation(randomId(), Space.EMPTY, Markers.EMPTY, annType, jContainer);
		}

		private static JRightPadded<Expression> packJRight(final Expression element) {
			return new JRightPadded<>(element, Space.SINGLE_SPACE, Markers.EMPTY);
		}

		private static J.Assignment convertAssigment(final J.Assignment x) {
			final String name = ((J.Identifier) x.getVariable()).getSimpleName();
			if ("description".equals(name)) {
				return sameConvertion("description", x);
			}
			throw new IllegalArgumentException("Unknown assignment " + name);
		}

		private static J.Assignment convertAssigmentModel(final J.Assignment x) {
			final String name = ((J.Identifier) x.getVariable()).getSimpleName();
			if ("name".equals(name)) {
				return sameConvertion("name", x);
			}
			if ("value".equals(name)) {
				return sameConvertion("description", x);
			}
			if ("required".equals(name)) {
				// TODO this one is deprecated, should use RequiredMode requiredMode
				return sameConvertion("required", x);
			}
			if ("example".equals(name)) {
				return sameConvertion("example", x);
			}
			if ("allowableValues".equals(name)) {
				// TODO : Moved to @Schema
				return null;
			}
			if ("hidden".equals(name)) {
				return sameConvertion("hidden", x);
			}
			throw new IllegalArgumentException("Unknown assignment " + name);
		}

		private static Assignment sameConvertion(final String string, final Assignment x) {
			final Expression lit = x.getAssignment();
			return createAssignment(string, lit);
		}

		private static Assignment createAssignment(final String attr, final Expression lit) {
			final J.Identifier variable = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, attr, null, null);
			final JLeftPadded<Expression> padded = new JLeftPadded<>(Space.EMPTY, lit, Markers.EMPTY);
			return new J.Assignment(randomId(), Space.EMPTY, Markers.EMPTY, variable, padded, JavaType.Primitive.String);
		}

	}
}
