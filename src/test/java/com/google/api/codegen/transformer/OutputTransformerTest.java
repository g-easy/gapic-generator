/* Copyright 2018 Google LLC
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
package com.google.api.codegen.transformer;

import static com.google.api.codegen.metacode.InitCodeContext.InitCodeOutputType;
import static com.google.api.codegen.transformer.OutputTransformer.accessorNewVariable;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.codegen.config.FieldConfig;
import com.google.api.codegen.config.FieldModel;
import com.google.api.codegen.config.MethodConfig;
import com.google.api.codegen.config.MethodContext;
import com.google.api.codegen.config.MethodModel;
import com.google.api.codegen.config.PageStreamingConfig;
import com.google.api.codegen.config.ProtoTypeRef;
import com.google.api.codegen.config.SampleConfig;
import com.google.api.codegen.config.SampleContext;
import com.google.api.codegen.config.SampleSpec;
import com.google.api.codegen.config.TypeModel;
import com.google.api.codegen.util.Name;
import com.google.api.codegen.util.Scanner;
import com.google.api.codegen.viewmodel.CallingForm;
import com.google.api.codegen.viewmodel.OutputView;
import com.google.api.tools.framework.model.TypeRef;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OutputTransformerTest {

  private OutputTransformer.ScopeTable parent;
  private OutputTransformer.ScopeTable child;
  private SampleContext sampleContext;
  private final CallingForm form = CallingForm.Request; // enums can't be mocked

  @Mock private FeatureConfig featureConfig;
  @Mock private FieldConfig resourceFieldConfig;
  @Mock private ImportTypeTable typeTable;
  @Mock private MethodConfig config;
  @Mock private MethodContext context;
  @Mock private MethodModel model;
  @Mock private PageStreamingConfig pageStreamingConfig;
  @Mock private SurfaceNamer namer;

  @Before
  public void setUp() {
    SampleConfig sampleConfig =
        SampleConfig.newBuilder()
            .id("test-sample-value-set-id")
            .type(SampleSpec.SampleType.STANDALONE)
            .build();
    sampleContext =
        SampleContext.newBuilder()
            .uniqueSampleId("test-sample-value-set-id")
            .sampleConfig(sampleConfig)
            .sampleType(SampleSpec.SampleType.STANDALONE)
            .callingForm(form)
            .initCodeOutputType(InitCodeOutputType.FieldList)
            .build();

    parent = new OutputTransformer.ScopeTable();
    child = new OutputTransformer.ScopeTable(parent);
    MockitoAnnotations.initMocks(this);
    when(context.getFeatureConfig()).thenReturn(featureConfig);
    when(context.getMethodConfig()).thenReturn(config);
    when(context.getMethodModel()).thenReturn(model);
    when(context.getNamer()).thenReturn(namer);
    when(context.getTypeTable()).thenReturn(typeTable);
    when(model.getSimpleName()).thenReturn("methodSimpleName");
    when(namer.getSampleResponseVarName(context, form)).thenReturn("sampleResponseVarName");
  }

  @Test
  public void testAccessorNewVariableFailWithReservedKeyword() {
    Scanner scanner = new Scanner("$resp");
    when(config.getPageStreaming()).thenReturn(pageStreamingConfig);
    when(pageStreamingConfig.getResourcesFieldConfig()).thenReturn(resourceFieldConfig);
    when(namer.getAndSaveElementResourceTypeName(typeTable, resourceFieldConfig))
        .thenReturn("ShelfBookName");
    when(featureConfig.useResourceNameFormatOption(resourceFieldConfig)).thenReturn(true);
    when(namer.getSampleUsedVarNames(context, form)).thenReturn(ImmutableSet.of("fooResponse"));
    try {
      OutputView.VariableView variableView =
          accessorNewVariable(scanner, context, sampleContext, parent, "fooResponse", false);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage())
          .contains(
              "cannot define variable \"fooResponse\": it is already used by the sample template"
                  + " for calling form");
    }
  }

  @Test
  public void testAccessorNewVariablePageStreamingResourceNameResponse() {
    Scanner scanner = new Scanner("$resp");

    when(config.getPageStreaming()).thenReturn(pageStreamingConfig);
    when(pageStreamingConfig.getResourcesFieldConfig()).thenReturn(resourceFieldConfig);
    when(namer.getAndSaveElementResourceTypeName(typeTable, resourceFieldConfig))
        .thenReturn("ShelfBookName");
    when(featureConfig.useResourceNameFormatOption(resourceFieldConfig)).thenReturn(true);

    OutputView.VariableView variableView =
        accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);

    assertThat(variableView.variable()).isEqualTo("sampleResponseVarName");
    assertThat(variableView.accessors()).isEmpty();
    assertThat(parent.getTypeName("newVar")).isEqualTo("ShelfBookName");
    assertThat(parent.getTypeModel("newVar")).isNull();
  }

  @Test
  public void testAccessorNewVariablePageStreamingResponse() {
    Scanner scanner = new Scanner("$resp");

    when(config.getPageStreaming()).thenReturn(pageStreamingConfig);
    when(pageStreamingConfig.getResourcesFieldConfig()).thenReturn(resourceFieldConfig);
    when(featureConfig.useResourceNameFormatOption(resourceFieldConfig)).thenReturn(false);
    FieldModel fieldModel = mock(FieldModel.class);
    when(resourceFieldConfig.getField()).thenReturn(fieldModel);
    TypeModel typeModel = mock(TypeModel.class);
    when(fieldModel.getType()).thenReturn(typeModel);
    when(typeModel.makeOptional()).thenReturn(typeModel);
    when(typeTable.getNicknameFor(typeModel)).thenReturn("TypeName");

    OutputView.VariableView variableView =
        accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);

    assertThat(variableView.variable()).isEqualTo("sampleResponseVarName");
    assertThat(variableView.accessors()).isEmpty();
    assertThat(parent.getTypeName("newVar")).isEqualTo("TypeName");
    assertThat(parent.getTypeModel("newVar")).isEqualTo(typeModel);
  }

  @Test
  public void testAccessorNewVariableResponse() {
    Scanner scanner = new Scanner("$resp");

    when(config.getPageStreaming()).thenReturn(null);
    TypeModel typeModel = mock(TypeModel.class);
    when(typeTable.getNicknameFor(typeModel)).thenReturn("TypeName");
    when(model.getOutputType()).thenReturn(typeModel);

    OutputView.VariableView variableView =
        accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);

    assertThat(variableView.variable()).isEqualTo("sampleResponseVarName");
    assertThat(variableView.accessors()).isEmpty();
    assertThat(parent.getTypeName("newVar")).isEqualTo("TypeName");
    assertThat(parent.getTypeModel("newVar")).isEqualTo(typeModel);
  }

  @Test
  public void testAccessorNewVariableResourceNameFromScopeTable() {
    assertThat(parent.put("old_var", null, "ShelfBookName")).isTrue();
    Scanner scanner = new Scanner("old_var");
    when(namer.localVarName(Name.from("old_var"))).thenReturn("oldVar");
    OutputView.VariableView variableView =
        accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);

    assertThat(variableView.variable()).isEqualTo("oldVar");
    assertThat(variableView.accessors()).isEmpty();
    assertThat(parent.getTypeName("newVar")).isEqualTo("ShelfBookName");
    assertThat(parent.getTypeModel("newVar")).isEqualTo(null);
  }

  @Test
  public void testAccessorNewVariableFromScopeTable() {
    TypeModel oldVarTypeModel = mock(TypeModel.class);
    assertThat(parent.put("old_var", oldVarTypeModel, "OldVarTypeName")).isTrue();
    Scanner scanner = new Scanner("old_var");
    when(namer.localVarName(Name.from("old_var"))).thenReturn("oldVar");
    when(typeTable.getNicknameFor(oldVarTypeModel)).thenReturn("OldVarTypeName");
    OutputView.VariableView variableView =
        accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);

    assertThat(variableView.variable()).isEqualTo("oldVar");
    assertThat(variableView.accessors()).isEmpty();
    assertThat(parent.getTypeName("newVar")).isEqualTo("OldVarTypeName");
    assertThat(parent.getTypeModel("newVar")).isEqualTo(oldVarTypeModel);
  }

  @Test
  public void testAccessorNewVariableWithAccessors() {
    Scanner scanner = new Scanner("old_var.property");
    when(namer.localVarName(Name.from("old_var"))).thenReturn("oldVar");
    TypeModel oldVarTypeModel = mock(TypeModel.class);
    assertThat(parent.put("old_var", oldVarTypeModel, "OldVarType")).isTrue();
    when(oldVarTypeModel.isMessage()).thenReturn(true);
    when(oldVarTypeModel.isRepeated()).thenReturn(false);
    when(oldVarTypeModel.isMap()).thenReturn(false);
    FieldModel propertyFieldModel = mock(FieldModel.class);
    when(oldVarTypeModel.getField("property")).thenReturn(propertyFieldModel);
    TypeModel propertyTypeModel = mock(TypeModel.class);
    when(namer.getFieldGetFunctionName(propertyFieldModel)).thenReturn("getProperty");
    when(typeTable.getNicknameFor(propertyTypeModel)).thenReturn("PropertyTypeName");
    when(namer.getFieldAccessorName(propertyFieldModel)).thenReturn(".getProperty()");
    when(propertyFieldModel.getType()).thenReturn(propertyTypeModel);

    OutputView.VariableView variableView =
        accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);

    assertThat(variableView.variable()).isEqualTo("oldVar");
    assertThat(variableView.accessors()).containsExactly(".getProperty()").inOrder();
    assertThat(parent.getTypeName("newVar")).isEqualTo("PropertyTypeName");
    assertThat(parent.getTypeModel("newVar")).isEqualTo(propertyTypeModel);
  }

  @Test
  public void testAccessorNewVariableScalarTypeForCollectionFail() {
    TypeModel oldVarTypeModel = mock(TypeModel.class);
    assertThat(parent.put("old_var", oldVarTypeModel, "OldVarTypeName")).isTrue();
    Scanner scanner = new Scanner("old_var");
    when(oldVarTypeModel.isRepeated()).thenReturn(false);
    when(namer.localVarName(Name.from("old_var"))).thenReturn("oldVar");
    when(namer.getAndSaveTypeName(typeTable, oldVarTypeModel)).thenReturn("OldVarTypeName");
    try {
      OutputView.VariableView variableView =
          accessorNewVariable(scanner, context, sampleContext, parent, "newVar", true);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("is not a repeated field");
    }
  }

  @Test
  public void testAccessorNewVariableWithIndex() {
    Scanner scanner = new Scanner("old_var.property[0]");
    when(namer.localVarName(Name.from("old_var"))).thenReturn("oldVar");
    TypeModel oldVarTypeModel = mock(TypeModel.class);
    assertThat(parent.put("old_var", oldVarTypeModel, "OldVarType")).isTrue();
    when(oldVarTypeModel.isMessage()).thenReturn(true);
    when(oldVarTypeModel.isRepeated()).thenReturn(false);
    when(oldVarTypeModel.isMap()).thenReturn(false);
    FieldModel propertyFieldModel = mock(FieldModel.class);
    when(oldVarTypeModel.getField("property")).thenReturn(propertyFieldModel);
    TypeModel propertyTypeModel = mock(TypeModel.class);
    when(namer.getFieldGetFunctionName(propertyFieldModel)).thenReturn("getProperty");
    when(propertyTypeModel.isRepeated()).thenReturn(true);
    TypeModel optionalPropertyTypeModel = mock(TypeModel.class);
    when(propertyTypeModel.makeOptional()).thenReturn(optionalPropertyTypeModel);

    when(typeTable.getNicknameFor(optionalPropertyTypeModel))
        .thenReturn("OptionalPropertyTypeName");
    when(namer.getFieldAccessorName(propertyFieldModel)).thenReturn(".getProperty()");
    when(namer.getIndexAccessorName(0)).thenReturn("[0]");
    when(propertyFieldModel.getType()).thenReturn(propertyTypeModel);

    OutputView.VariableView variableView =
        accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);

    assertThat(variableView.variable()).isEqualTo("oldVar");
    assertThat(variableView.accessors()).containsExactly(".getProperty()", "[0]").inOrder();
    assertThat(parent.getTypeName("newVar")).isEqualTo("OptionalPropertyTypeName");
    assertThat(parent.getTypeModel("newVar")).isEqualTo(optionalPropertyTypeModel);
  }

  @Test
  public void testAccessorNewVariableMapKeyInvalidBooleanFail() {
    Scanner scanner = new Scanner("old_var.property{not_boolean}");
    when(namer.localVarName(Name.from("old_var"))).thenReturn("oldVar");
    TypeModel oldVarTypeModel = mock(TypeModel.class);
    assertThat(parent.put("old_var", oldVarTypeModel, "OldVarType")).isTrue();
    when(oldVarTypeModel.isMessage()).thenReturn(true);
    when(oldVarTypeModel.isRepeated()).thenReturn(false);
    when(oldVarTypeModel.isMap()).thenReturn(false);
    FieldModel propertyFieldModel = mock(FieldModel.class);
    when(oldVarTypeModel.getField("property")).thenReturn(propertyFieldModel);
    TypeModel propertyTypeModel = mock(TypeModel.class);
    when(propertyFieldModel.getType()).thenReturn(propertyTypeModel);
    when(propertyTypeModel.isRepeated()).thenReturn(true);
    when(propertyTypeModel.isMap()).thenReturn(true);
    when(namer.getFieldGetFunctionName(propertyFieldModel)).thenReturn("getProperty");
    when(namer.getFieldAccessorName(propertyFieldModel)).thenReturn(".getProperty()");
    TypeModel boolTypeModel = ProtoTypeRef.create(TypeRef.fromPrimitiveName("bool"));
    TypeModel stringTypeModel = ProtoTypeRef.create(TypeRef.fromPrimitiveName("string"));
    when(propertyTypeModel.getMapKeyType()).thenReturn(boolTypeModel);
    when(propertyTypeModel.getMapValueType()).thenReturn(stringTypeModel);
    try {
      accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("Could not assign value 'not_boolean' to type bool");
    }
  }

  @Test
  public void testAccessorNewVariableMapKeyUnquoatedStringFail() {
    Scanner scanner = new Scanner("old_var.property{unquoted_string}");
    when(namer.localVarName(Name.from("old_var"))).thenReturn("oldVar");
    TypeModel oldVarTypeModel = mock(TypeModel.class);
    assertThat(parent.put("old_var", oldVarTypeModel, "OldVarType")).isTrue();
    when(oldVarTypeModel.isMessage()).thenReturn(true);
    when(oldVarTypeModel.isRepeated()).thenReturn(false);
    when(oldVarTypeModel.isMap()).thenReturn(false);
    FieldModel propertyFieldModel = mock(FieldModel.class);
    when(oldVarTypeModel.getField("property")).thenReturn(propertyFieldModel);
    TypeModel propertyTypeModel = mock(TypeModel.class);
    when(propertyFieldModel.getType()).thenReturn(propertyTypeModel);
    when(propertyTypeModel.isRepeated()).thenReturn(true);
    when(propertyTypeModel.isMap()).thenReturn(true);
    when(namer.getFieldGetFunctionName(propertyFieldModel)).thenReturn("getProperty");
    when(namer.getFieldAccessorName(propertyFieldModel)).thenReturn(".getProperty()");
    TypeModel stringTypeModel = ProtoTypeRef.create(TypeRef.fromPrimitiveName("string"));
    when(propertyTypeModel.getMapKeyType()).thenReturn(stringTypeModel);
    when(propertyTypeModel.getMapValueType()).thenReturn(stringTypeModel);
    try {
      accessorNewVariable(scanner, context, sampleContext, parent, "newVar", false);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("expected string type for map key");
    }
  }

  @Test
  public void testScopeTablePut() {
    TypeModel stringTypeModel = ProtoTypeRef.create(TypeRef.fromPrimitiveName("string"));
    assertThat(parent.put("str", stringTypeModel, "String")).isTrue();
  }

  @Test
  public void testScopeTablePutFail() {
    TypeModel stringTypeModel = ProtoTypeRef.create(TypeRef.fromPrimitiveName("string"));
    assertThat(parent.put("str", stringTypeModel, "String")).isTrue();
    assertThat(parent.put("str", stringTypeModel, "String")).isFalse();
  }

  @Test
  public void testScopeTableGetTypeModel() {
    TypeModel stringTypeModel = ProtoTypeRef.create(TypeRef.fromPrimitiveName("string"));
    assertThat(parent.put("str", stringTypeModel, "String")).isTrue();
    assertThat(parent.getTypeModel("str")).isEqualTo(stringTypeModel);
    assertThat(parent.getTypeName("str")).isEqualTo("String");

    assertThat(parent.getTypeModel("book")).isNull();
    assertThat(parent.getTypeName("book")).isNull();
  }

  @Test
  public void testScopeTablePutAndGetResourceName() {
    assertThat(parent.put("book", null, "ShelfBookName")).isTrue();
    assertThat(parent.getTypeModel("book")).isEqualTo(null);
    assertThat(parent.getTypeName("book")).isEqualTo("ShelfBookName");
  }

  @Test
  public void testScopeTableGetFromParent() {
    TypeModel stringTypeModel = ProtoTypeRef.create(TypeRef.fromPrimitiveName("string"));
    assertThat(parent.put("str", stringTypeModel, "String")).isTrue();
    assertThat(parent.put("book", null, "ShelfBookName")).isTrue();

    assertThat(child.getTypeModel("str")).isEqualTo(stringTypeModel);
    assertThat(child.getTypeName("str")).isEqualTo("String");
    assertThat(child.getTypeModel("book")).isEqualTo(null);
    assertThat(child.getTypeName("book")).isEqualTo("ShelfBookName");
  }
}
