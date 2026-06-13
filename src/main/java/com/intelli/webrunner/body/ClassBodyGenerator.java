package com.intelli.webrunner.body;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.InheritanceUtil;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a sample JSON body (as nested {@link Map}/{@link List} values) from a Java {@link PsiClass},
 * honoring a subset of Jackson annotations. Contains no UI; callers supply the chosen class and options.
 */
public final class ClassBodyGenerator {

	public Map<String, Object> buildBody(
		PsiClass psiClass,
		boolean includeInherited,
		boolean useAnnotations,
		boolean useNulls
	) {
		return buildBodyForClass(psiClass, new HashSet<>(), 0, includeInherited, useAnnotations, useNulls);
	}

	private Map<String, Object> buildBodyForClass(
		PsiClass psiClass,
		Set<String> visiting,
		int depth,
		boolean includeInherited,
		boolean useAnnotations,
		boolean useNulls
	) {
		if (psiClass == null) {
			return Map.of();
		}
		String key = psiClass.getQualifiedName() == null ? psiClass.getName() : psiClass.getQualifiedName();
		if (key != null) {
			if (visiting.contains(key)) {
				return Map.of();
			}
			visiting.add(key);
		}
		if (depth > 4) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		Set<String> ignored = useAnnotations ? ignoredJsonProperties(psiClass) : Set.of();

		if (psiClass.isRecord()) {
			for (PsiRecordComponent component : psiClass.getRecordComponents()) {
				String name = jsonName(component.getName(),
									   component.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty"),
									   useAnnotations
				);
				if (ignored.contains(name)) {
					continue;
				}
				result.put(
					name,
					valueForType(component.getType(),
								 visiting,
								 depth + 1,
								 includeInherited,
								 useAnnotations,
								 useNulls
					)
				);
			}
		} else {
			PsiField[] fields = includeInherited ? psiClass.getAllFields() : psiClass.getFields();
			for (PsiField field : fields) {
				if (field.hasModifierProperty(PsiModifier.STATIC)) {
					continue;
				}
				if (field.hasModifierProperty(PsiModifier.TRANSIENT)) {
					continue;
				}
				if (useAnnotations && field.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnore") != null) {
					continue;
				}
				String name = jsonName(field.getName(),
									   field.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty"),
									   useAnnotations
				);
				if (ignored.contains(name)) {
					continue;
				}
				result.put(
					name,
					valueForType(field.getType(),
								 visiting,
								 depth + 1,
								 includeInherited,
								 useAnnotations,
								 useNulls
					)
				);
			}
		}

		if (key != null) {
			visiting.remove(key);
		}
		return result;
	}

	private Object valueForType(
		PsiType type,
		Set<String> visiting,
		int depth,
		boolean includeInherited,
		boolean useAnnotations,
		boolean useNulls
	) {
		if (type == null) {
			return null;
		}
		if (useNulls) {
			return null;
		}
		if (type.equals(PsiTypes.booleanType())) {
			return false;
		}
		if (type.equals(
			PsiTypes.byteType())
			|| type.equals(PsiTypes.shortType())
			|| type.equals(PsiTypes.intType())
			|| type.equals(PsiTypes.longType())
		) {
			return 0;
		}
		if (
			type.equals(PsiTypes.floatType())
				|| type.equals(PsiTypes.doubleType())
		) {
			return 0.0;
		}
		if (type.equals(PsiTypes.charType())) {
			return "a";
		}
		if (type instanceof PsiArrayType) {
			PsiType component = ((PsiArrayType) type).getComponentType();
			return List.of(valueForType(component, visiting, depth + 1, includeInherited, useAnnotations, useNulls));
		}
		if (type instanceof PsiClassType classType) {
			PsiClass resolved = classType.resolve();
			if (resolved == null || resolved instanceof PsiTypeParameter) {
				return null;
			}
			String qName = resolved.getQualifiedName();
			if (qName != null) {
				switch (qName) {
					case "java.lang.String", "java.lang.CharSequence" -> {
						return "";
					}
					case "java.lang.Boolean" -> {
						return false;
					}
					case "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte" -> {
						return 0;
					}
					case "java.lang.Float", "java.lang.Double" -> {
						return 0.0;
					}
					case "java.math.BigDecimal" -> {
						return "0.00";
					}
					case "java.math.BigInteger" -> {
						return "0";
					}
					case "java.util.UUID" -> {
						return UUID.randomUUID().toString();
					}
					case "java.time.LocalDate" -> {
						return "2024-01-01";
					}
					case "java.time.LocalDateTime" -> {
						return "2024-01-01T00:00:00";
					}
					case "java.time.OffsetDateTime" -> {
						return "2024-01-01T00:00:00Z";
					}
					case "java.time.Instant" -> {
						return "2024-01-01T00:00:00Z";
					}
				}
			}
			if (resolved.isEnum()) {
				for (PsiField field : resolved.getFields()) {
					if (field instanceof com.intellij.psi.PsiEnumConstant) {
						return field.getName();
					}
				}
				return "";
			}
			if (InheritanceUtil.isInheritor(resolved, "java.util.Map")) {
				PsiType[] params = classType.getParameters();
				Object keySample = params.length > 0 ?
					valueForType(params[0], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : "key";
				Object valueSample = params.length > 1 ?
					valueForType(params[1], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : "";
				if (keySample == null) {
					keySample = "key";
				}
				return Map.of(String.valueOf(keySample), valueSample);
			}
			if (InheritanceUtil.isInheritor(resolved, "java.util.Collection")) {
				PsiType[] params = classType.getParameters();
				Object item = params.length > 0 ?
					valueForType(params[0], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : null;
				return item == null ? List.of() : List.of(item);
			}
			if (InheritanceUtil.isInheritor(resolved, "java.util.Optional")) {
				PsiType[] params = classType.getParameters();
				return params.length > 0 ?
					valueForType(params[0], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : null;
			}
			return buildBodyForClass(resolved, visiting, depth + 1, includeInherited, useAnnotations, useNulls);
		}
		return null;
	}

	private String jsonName(
		String fallback,
		com.intellij.psi.PsiAnnotation annotation,
		boolean useAnnotations
	) {
		if (!useAnnotations || annotation == null) {
			return fallback;
		}
		com.intellij.psi.PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
		if (value != null) {
			String text = value.getText();
			if (text != null && text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
				return text.substring(1, text.length() - 1);
			}
		}
		return fallback;
	}

	private Set<String> ignoredJsonProperties(PsiClass psiClass) {
		com.intellij.psi.PsiAnnotation annotation =
			psiClass.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnoreProperties");
		if (annotation == null) {
			return Set.of();
		}
		Set<String> result = new HashSet<>();
		com.intellij.psi.PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
		if (value instanceof com.intellij.psi.PsiArrayInitializerMemberValue array) {
			for (com.intellij.psi.PsiAnnotationMemberValue entry : array.getInitializers()) {
				String text = entry.getText();
				if (text != null && text.startsWith("\"") && text.endsWith("\"")) {
					result.add(text.substring(1, text.length() - 1));
				}
			}
		} else if (value != null) {
			String text = value.getText();
			if (text != null && text.startsWith("\"") && text.endsWith("\"")) {
				result.add(text.substring(1, text.length() - 1));
			}
		}
		return result;
	}
}
