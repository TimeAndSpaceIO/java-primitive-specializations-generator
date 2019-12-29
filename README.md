JPSG is temporarily extracted from [Koloboke tree](
https://github.com/leventov/Koloboke/tree/302e6d5e5889a80edb3aeed2e664f480439f8155/jpsg) to
make new releases.

# Java Primitive Specializations Generator

Java Primitive Specializations Generator (JPSG) is a template processor with the primary goal of
producing code specialized for any Java primitive types and for object (generic) types, while Java
doesn't have generics over primitives.

Source code of [the Koloboke collections library](
https://github.com/leventov/Koloboke) is generated with JPSG. The difference between JPSG and
similar template processing engines powering other libraries of collections for primitive types in
Java (fastutil, Eclipse, Trove, HPPC - all have their own template processing engine) is that JPSG's
template files can be *valid Java source files* that correspond to one of the specializations
produced from the respective template.

This allows to make the development of templates more convenient and less error prone by configuring
directories with template files as *source directories* in your IDE so that IDE features like
autocomplete and inspections will work in the template files. On the other hand, IDE will think that
there are duplicate classes in the project (because a template file's name (class name) and package
are the same as in one of the generated specializations), so you will lose the ability to build the
project from IDE (you will need to use Maven or Gradle every time to build the project).

This is possible because JPSG captures and replaces actual type occurrences in code
instead of relying on patterns that can't be part of Java code. For example, the following file
`CharIntPair.java` is a valid JPSG template:
```java
/* with char|byte|short|int|long|float|double|object key
        int|byte|char|short|long|float|double|object value */
/* if !(object key object value) */ // Excludes ObjectObjectPair, because there is Pair already

interface CharIntPair/*<>*/ extends Pair<Character, Integer> {
  /* if !(object key) */ char getCharKey(); /* endif */
  /* if !(object value) */ int getIntValue(); /* endif */
}
```

JPSG produces the following specialization `FloatObjectPair.java` from the above template, along
with 62 others:
```java
interface FloatObjectPair<V> extends Pair<Float, V> {
  float getFloatKey();
}
```

As you can see in this example, the second thing that enables JPSG's templates to be valid Java code
is that control structures such as `/* if ... */ ... /* endif */` use Java comment syntax. (More on
this in the section about `/* if */` blocks in the tutorial below.)

The above example highlights another difference between JPSG and some other template processing
engines: JPSG allows specialization for primitive types as well as object (generic) types. This
makes it possible to produce all possible specializations of types such as `Pair<A, B>`,
`Map<K, V>`, `Triple<A, B, C>`, etc. from a single source template, while with some other template
processing engines usually 4 or 8 different templates are required to cover all {object, primitive}
combinations across all dimensions.

Although primarily designed for specializing Java types, JPSG also supports specialization over
arbitrarily defined (non Java type) "dimensions".  For example, for the following template file
`Foo.java`:
```java
/* with Foo|Bar myDimension */
class Foo {
  void getFoo() {
    int foo = /* if Foo myDimension */0// elif Bar myDimension //1// endif */;
    return foo;
  }
}
```
JPSG produces `Foo.java`:
```java
class Foo {
  void getFoo() {
    int foo = 0;
    return foo;
  }
}
```
and `Bar.java`:
```java
class Bar {
  void getBar() {
    int bar = 1;
    return bar;
  }
}
```

Dimensions for specializing Java type options and dimensions for specializing non Java type options
can be mixed in a single JPSG template file.

<hr>

JPSG is available as a [Gradle plugin](#gradle-plugin) and a [Maven plugin](
https://github.com/TimeAndSpaceIO/jpsg-maven-plugin).

## Tutorial

### Template files

By convention, in both Gradle and Maven JPSG plugins JPSG template files are located in
`src/main/javaTemplates/com/mycompany/MyShortSpecializedType.java`, as well as
`src/test/javaTemplates/...`. In other words, conventional template directories `javaTemplates/` are
sibling to conventional `java/` directories. The package structure is reflected in the directory
tree structure as well as in ordinary sources. Gradle plugin additionally supports
`resourceTemplates/`, for example
`src/main/resourceTemplates/META-INF/services/com.mycompany.MyShortSpecializedType`.

A template file usually begins with a `/* with */` construction that defines specialization
dimensions of the template. For example:
```java
/* with char|byte|short|int|long|float|double|object key
        int|byte|char|short|long|float|double|object value */
/* license header */
package com.mypackage;
...
```
Here there are two *dimension definitions*: *dimension* "key" has
"char|byte|short|int|long|float|double|object" *options*, dimension "value" has
"int|byte|char|short|long|float|double|object" options. Another example:
```java
/* with integer|long|double|obj tType long|double|integer|obj uType Immutable|Mutable mutability */
...
```
Here dimension "tType" has "integer|long|double|obj" options, dimension "uType" has
"long|double|integer|obj" and dimension "mutability" has "Immutable|Mutable" options.

<a name="dimensions-bnf"></a>
Here is the formal BNF syntax of dimension definitions:
```
<dimensionName> := alphanumeric string
<javaTypeOption> := "bool" "ean"? | "byte" | "char" "acter"? | "short" | "int" "eger"? |
                    "long" | "float" | "double" | "obj" "ect"?
<nonJavaTypeOption> := alphanumeric string which isn't a <javaTypeOption>
<javaTypeOptions> := ( <javaTypeOption> "|" )* <javaTypeOption>
<nonJavaTypeOptions> := ( <nonJavaTypeOption> "|" )* <nonJavaTypeOption>
<options> := (<javaTypeOptions> | <nonJavaTypeOptions>)
<dimension> := <options> " " <dimensionName>
<dimensions> := ( <dimension> " "+ )* <dimension>
```

The first option in the `|`-delimited list defined for each dimension is the *source* option. In the
previous example, the source option of dimension "tType" is "integer", "long" of "uType" dimension
and "Immutable" of "mutability" dimension.

JPSG searches for occurrences of the source options in the contents *and the file name* of the
template file and replaces them to produce specializations corresponding to all combinations of
options for all dimensions (a cartesian product), except, maybe, some combinations that are
filtered. See the section about `/* if */` blocks below for more info about filtering.

The file name can be customized further with a special `/* define ClassName */` construction. See
the section about `/* define */` blocks below.

<a name="with-default-types"></a>
If there is no `/* with */` block in the beginning of a template file, JPSG attempts to deduce
dimensions from the name of the template file, based on `defaultTypes` configuration (available in
both Gradle and Maven plugins) that defaults to "byte|char|short|int|long|float|double", i. e. all
primitive numeric Java types. For example, for a template file named `FloatShortIntTriple.java`
deduced dimension definitions will be:
```
float|byte|char|short|int|long|double t
short|byte|char|int|long|float|double u
int|byte|char|short|long|float|double v
```

JPSG doesn't deduce dimensions with non Java type options from the template file name to enforce
giving these dimensions more meaningful names than "t", "u", and "v". `/* with */` block should be
used to specify non Java type dimensions. And if such block is present,
JPSG *doesn't* try to deduce more dimensions from the template file name combine them with
explicitly defined dimensions.

If there is no `/* with */` block in the beginning of a template file and its file name doesn't
include parts that correspond to java primitive type names, JPSG assumes that there are no file-wide
dimensions for specialization. In this case JPSG generates exactly one output file from the template
with the same name. It's not pointless because such template file could have inner generation,
for example:
```java
class PrimitiveUtils {
  /* with byte|char|short|int|long|float|double primType */

  ... some code that repeats for each primitive type

  /* endwith */
}
```

See the section about `/* with */` blocks below.

### Specialization rules

Each dimension can have either all *Java type options* or all *non Java type options* (see BNF
definitions of `<javaTypeOptions>` and `<nonJavaTypeOptions>` above). That is,
`int|long|Foo myDimension` is an illegal dimension definition for JPSG because there are both Java
type options "int", "long" and a non Java type option "Foo". JPSG specializes dimensions with Java
type options (also called *Java type dimensions*) differently than dimensions with non Java type
options.

#### Specialization of Java type dimensions

Java type options are "bool", "boolean", "byte", "short", "char", "character", "int",
"integer", "long", "float", "double", "obj", and "object". Java type dimension specifies a subset of
those options. "bool"/"boolean", "char"/"character", "int"/"integer", and "obj"/"object" can't both
appear in one dimension definition (but there is a difference between the short and long forms -
see below.) "bool"/"boolean" and "obj"/"object" can't be source options, i. e. they can't go first
in the options list of a dimension.

JPSG analyzes the template and replaces primitive type appearances in all possible forms (the
following examples assume that the source option is "char" or "character"):
 - Primitive Java type occurrences: `char value = ...`, `public char myFunction();`
 - Boxed primitive class occurrences: `List<Character> myValues = ...`,
   `/** @see Character#MAX_VALUE */`
 - Lowercase: "char" and "character", title case: "Char" and "Character", and upper case: "CHAR" and
   "CHARACTER" appearances in any Java identifiers (variable names, method names, class names, block
   labels, etc), as well as in all other (non Java) contexts in the template file, such as in
   comments and inside string literals. Plural forms: "chars", "characters" are recognized.
   Examples: `Object myChar = ...`, `int numChars = ...`,
   `void sortCharacters();`, `interface CharFunctor ...`, `int CHARACTER_LIMIT = ...`,
   `/** Get a char value. */`.

Specialization to "obj"/"object" by default replaces both primitive Java type occurrences (`char`)
and boxed primitive class occurrences (`Character`) with a single letter generic parameter: the
first letter of the dimension name. For example, for dimension `char|object key`, template
```java
  char getChars() { List<Character> list = getList(); return list.get(0); }
```
is specialized to "object" as
```java
  K getObjects() { List<K> list = getList(); return list.get(0); }
```

For this reason, dimensions that have "object" or "obj" options must have names all starting with
different letters.

If some primitive type occurrence or a boxed primitive class occurrence should be replaced with
`Object` rather than a generic parameter reference, that occurrence should be prepended with
a special `/* raw */` modifier in the template:
```java
  /* raw */char getChars() { List</* raw */Character> list = getList(); return list.get(0); }
```
is specialized to "object" as
```java
  Object getObjects() { List<Object> list = getList(); return list.get(0); }
```

Choosing either of the options in "bool"/"boolean", "char"/"character", "int"/"integer", and
"obj"/"object" pairs affects how the specialization for the corresponding option appears in
identifiers: `short getShort();` (assuming the source option is "short") could be specialized as
either `int getInt();` or `int getInteger();`, depending on whether "int" or "integer" option is
specified for the dimension. If the source option also allows two potential variants (i. e. the
source option is either "char", "character", "int" or "integer", because booleans and objects can't
be source options), the occurrences in the template that correspond to the specified source option
are recognized as neutral, other occurrences are recognized as deliberately long or short:

Source option | Occurrence in template | Recognized as | Target option | Specialization outcome
------------- | ---------------------- | ------------- | ------------- | ----------------------
"char"        | `CharCursor`           | neutral       | "bool"        | `BoolCursor`
"char"        | `CharCursor`           | neutral       | "boolean"     | `BooleanCursor`
"char"        | `CharacterCursor`      | long          | "bool"        | `BooleanCursor`
"char"        | `CharacterCursor`      | long          | "boolean"     | `BooleanCursor`
"character"   | `CharCursor`           | short         | "bool"        | `BoolCursor`
"character"   | `CharCursor`           | short         | "boolean"     | `BoolCursor`
"character"   | `CharacterCursor`      | neutral       | "bool"        | `BoolCursor`
"character"   | `CharacterCursor`      | neutral       | "boolean"     | `BooleanCursor`

It's often convenient to choose "short" (also "char" and "byte", if the template has several Java
type dimensions, such as Map, Pair, Triple, etc.) as the source option, because `short` is rarely
used in Java code, so there will be little type occurrences that need to be excluded from
specialization (see the section about `/* with */` blocks below). If identifiers in different styles
(`getChar` vs `getCharacter` - see above) appear in the code, choosing "char"/"character" and
"int"/"integer" as source options might allow to express the specialization logic without `/* if */`
and `/* define */` blocks.

#### Non Java type dimensions

Options of non Java type dimensions intended for specialization must start with a capital letter:
`/* with Foo|Bar myDimension */`, not `/* with foo|bar myDimension */` (but lowercase non Java type
options can be used to mask outer dimensions - see the [section about `/* with */` blocks below
](#-with--blocks), and in conjunction with generics structures like `/*<>*/` - see the
[corresponding section below](#generics-structures--extends-super-)). Options can include several
words in camel case: `/* with NoElements|SomeElements numElements */`. JPSG replaces textual
occurrences of source non Java type options in three forms:
 - Camel case: "NoElements", "Foo".
 - Lower case: "noElements", "foo".
 - Upper case with underscores between words: "NO_ELEMENTS", "FOO".

### Block control structures

There are three kinds of block control structures in JPSG: `/* with */`, `/* if */` and
`/* define */`. They should be paired with corresponding `/* endwith */`, `/* endif */` and
`/* enddefine */`, like `/* with ... */ void myMethod() { ... }  /* endwith */`. The exceptions are
`/* with */` blocks in the very beginning of template files (see the section about template files
above), possibly directly followed by an `/* if */` block - these two shouldn't be closed.

Block control structures should be correctly nested in the template file with respect to each other.

#### `/* with */` blocks

The syntax of `/* with */` blocks is already described in the section about template files above.
The only difference between a `/* with */` block in the beginning of a template file and
`/* with */` blocks inside a template file is that the latter should be paired with a closing
`/* endwith */`. Code between the tags is repeated and specialized for each combination of options
of all dimensions defined in the `/* with */` block (a cartesian product).

`/* with */` blocks can be nested. If a nested `/* with */` block has a definition of a dimension
with the same name as some dimension defined in an outer `/* with */` block (including the one in
the beginning of the template file), the inner definition shadows the outer definition. This allows
to exclude some option occurrences from specialization if they don't belong to the specialization
domain:
```java
/* with int|long element */
void sort(int[] array) {
  /* with not element */int/* endwith */ len = array.length;
  ...
}
/* endwith */
```
In the example above, we don't want array's length to become `long` when we sort arrays of longs.
Such occurrences that don't belong to the specialization domain are enclosed between
`/* with not myDimensionName */.../* endwith */`. There is nothing special about "not" - this is
just a sole non Java type option of a `myDimensionName` definition that shadows the dimension in
some outer `/* with */` block. It could have been `/* with quoted myDimensionName */`,
`/* with notSpecialized myDimensionName */`, etc - whatever you find more readable.

#### `/* if */` blocks

JPSG matches conditions in `/* if */` blocks against the dimension options in the context (also
called *dimensions context*) to generate different variants of code (or none at all). Here are some
examples that show different features of `/* if */` blocks:
```java
/* if int|long|double elem */
java.util.function.ToIntFunction f = ...
/* elif object elem //
Function<E> f = ...
// elif byte|char|short|float elem //
com.mypackage.ToIntFunction f = ...
// endif */
```

```java
/* if !(obj key) || !(obj value) //
ByteShortPair//<>// pair = ...
// elif obj key obj value */
Pair/*<>*/ pair = ...
/* endif */
```

There can be some `/* elif */` branches between `/* if */` and `/* endif */`, each with it's own
condition. Note that there are no "else" branches. If the condition in the `/* if */` doesn't match
the current dimensions context, nor any of the conditions in the `/* elif */` branches, the whole
`/* if */ ... /* endif */` is skipped by JPSG. No code from inside it appears in the specialization
output file.

Here is the formal BNF syntax of conditions allowed in `/* if */` and `/* elif */` (it references
`<dimensions>` formalized above, i. e. syntactically equivalent to dimension definitions that appear
in `/* with */` blocks):
<a name="conditions-bnf"></a>
```
<simple-condition> := <dimensions>
<negated-condition> := "!(" <simple-condition> ")"
<condition-in-parens> := "(" <simple-condition> ")" | <negated-condition>
<disjunction-condition> := ( <condition-in-parens> " || " )+ <condition-in-parens>
<conjunction-condition> := ( <condition-in-parens> " && " )+ <condition-in-parens>
<condition> := <simple-condition> | <negated-condition> | <disjunction-condition> | <conjunction-condition>
```

`<simple-condition>` has the following semantics: a condition that has `dimension_1` ..
`dimension_N` with options `option_1_1` .. `option_1_M1`, `option_2_1` .. `option_2_M2`, ...,
`option_N_1` .. `option_N_Mn` respectively *matches* a specialization context
`dimension_in_context_1` = `target_option_1`, ..., `dimension_in_context_K` = `target_option_K` if
and only if for each of `i` between 1 and N, if there is `dimension_in_context_j` equal to
`dimension_i`, then `target_option_j` appears to the list `option_i_1` .. `option_i_Mi`.

Putting that a little more intuitively, a condition like
```
option_1_1|...|option_1_M1 dimension_1
option_2_1|...|option_2_M2 dimension_2
...
option_N_1|...|option_N_Mn dimension_N
```
should be read as "if dimension_1 *is* option_1_1 *or* option_1_2 *or* ... option_1_M1, *and*
dimension_2 *is* ..., *and* dimension_N *is* ...".

Semantics of `<negated-condition>`, `<disjunction-condition>`, and `<conjunction-condition>`
correspond to semantics of syntactically identical operations with boolean values in Java code.

Conditions with nested parentheses are not supported in JPSG, but it's possible to express any
condition in a disjunctive normal form.

So the conditions from the examples in the beginning of this section have the following meanings:
 - `int|long|double elem`: if elem is "int" or "long" or "double".
 - `object elem`: if elem is "object".
 -  `!(obj key) || !(obj value)`: if key is not "obj" or value is not "obj".
 - `obj key obj value`: if key is "obj" and value is "obj".

All control structures in JPSG can start and end with `//` as well as `/*` and `*/`. This is
particularly useful in `/* if */` blocks, where choosing how each `if` and `elif` branch starts and
ends allows to "comment out" some branches to keep the template valid Java code. For example, here
is an `/* if */` block from one of the examples above:
```java
int foo = /* if Foo myDimension */0// elif Bar myDimension //1// endif */;
```
Note that the `elif` part starts and ends with `//`, and the `endif` part starts with `//` and ends
with `*/`, so that `1` in the `elif` branch is commented out in the template file, if it is treated
as Java code. Alternatively, the `if` branch could have been commented out as:
```java
int foo = /* if Foo myDimension //0// elif Bar myDimension */1/* endif */;
```
You may choose to always use `/*` and `*/` because in the end of the day JPSG template files are not
compiled with `javac`, but then your IDE might think that there are errors in the template files, if
the IDE is configured to recognize the template files as Java code.

<a name="template-file-beginning-if"></a>
In the beginning of a template file, right after a `/* with */` block, there may be an `/* if */`
block. When the condition in this `/* if */` block doesn't match a dimensions context from the
power set determined by the preceding `/* with */` block, a specialization file for this dimensions
context is not generated. An example from the beginning of this document:
```java
/* with char|byte|short|int|long|float|double|object key
        int|byte|char|short|long|float|double|object value */
/* if !(object key object value) */ // Excludes ObjectObjectPair, because there is Pair already

interface CharIntPair ...
```
Here the dimension context "key=object,value=object" doesn't match the condition
`!(object key object value)`, therefore `ObjectObjectPair.java` specialization is not generated.

`/* if */` block in the beginning of a template file can't have `/* elif */` branches and must not
have a closing `/* endif */`.

#### /* define */ blocks

`/* define */` blocks are used to create shortcuts for some lengthy template code (usually involving
`/* if */` blocks) and use them in multiple places. For example:
```java
/* with byte|char|short|int|long|float|double|object key
        short|byte|char|int|long|float|double|object value */

/* define MapType //
// if object key object value //MutableMap<Object, Object>
// elif object key //MutableObjectShortMap<Object>
// elif object value //MutableByteObjectMap<Object>
// elif !(object key) && !(object value) //MutableByteShortMap
// endif //
// enddefine */

/*MapType*/MutableByteShortMap/**/ myMap1 = new /*MapType*/MutableByteShortMap/**/();
/*MapType*/MutableByteShortMap/**/ myMap2 = new /*MapType*/MutableByteShortMap/**/();
```

The word after `define` is the name of the definition. After the closing `enddefine`, JPSG replaces
`/* <definition-name> */` and the following code until `/**/` with the template code between
`/* define // ... // enddefine */`. The opening tag may also start or end with `//` and the closing
tag may also be `/*//`, `//*/`, or `////`, as well as for other control structures in JPSG. Spaces
around the `<definition-name>` are optional: the definition use may also be `/*<definition-name>*/`.

If there is no closing `/**/` after the definition use, only the construction
`/* <definition-name> */` is replaced without "consuming" any following code until `/**/`.

Note: the text or code between `/* <definition-name> */` and `/**/` must not contain `/` character. 

If a definition called "ClassName" appears somewhere in the template file, JPSG uses the replacement
with leading and trailing whitespace removed concatenated with ".java" as a specialization file
name. For example, consider the following template `ByteShortPair.java`:
```java
/* with byte|short|char|int|long|float|double|object key
        short|byte|char|int|long|float|double|object value */

/* define ClassName //
// if !(object key object value) //ByteShortPair// elif object key object value //Pair// endif //
// enddefine */

interface /*ClassName*/ByteShortPair/**//*<>*/ {
  byte getKey();
  short getValue();
}
```

JPSG produces a specialization file called `Pair.java` rather than `ObjectObjectPair.java` from this
template, along with other 63 specializations.

`/* define */` blocks can also have one text parameter, like macros in some programming languages.
Consider the following template:
```java
/* with Preconditions|Assert|No parameterChecks */

/* define checkNonNull x */
/* if Preconditions parameterChecks //
    Preconditions.checkNonNull(x);
// elif Assert parameterChecks //
    assert x != null;
// endif */ /* comment // if parameterChecks=No, not emitting anything //*/
/* enddefine */

void myFirstMethodPreconditionsChecks(String arg1, String arg2) {
    /* checkNonNull arg1 */
    /* checkNonNull arg2 */
}

void myOtherMethodPreconditionsChecks(MyClass my) {
    /* checkNonNull my */
}

/* endwith */
```

JPSG produces the following specialization from this template:
```java
void myFirstMethodPreconditionsChecks(String arg1, String arg2) {
    Preconditions.checkNonNull(arg1);
    Preconditions.checkNonNull(arg2);
}

void myOtherMethodPreconditionsChecks(MyClass my) {
    Preconditions.checkNonNull(my);
}

void myFirstMethodAssertChecks(String arg1, String arg2) {
    assert arg1 != null;
    assert arg2 != null;
}

void myOtherMethodAssertChecks(MyClass my) {
    assert my != null;
}

void myFirstMethodNoChecks(String arg1, String arg2) {
}

void myOtherMethodNoChecks(MyClass my) {
}
```

The above example also features a definition named "comment" that always exists in the JPSG's
context. It has empty body, so it is replaced with nothing. It is useful to consume the text between
`/* comment //` and the following `//*/` not letting it into the generated code. This effectively
works as template-level comments.

### Auxiliary non-block structures and modifiers

#### Generics structures: `/*<>*/`, `/*<extends>*/`, `/*<super>*/`, `/*<?>*/`

Generics structures are useful when generating code for both primitive and object (generic) types
from the same template. `/*<>*/` has already been used in several examples above in this tutorial.
JPSG replaces `/*<>*/` with generics corresponding to first letters of the dimension names, for all
Java type dimensions that are specialized to "object" or "obj" in the current context. If some Java
type dimension is specialized to a primitive type, it's omitted from the generics list. If all Java
type dimensions are specialized to primitive types, JPSG replaces `/*<>*/` (three other variants of
the generics structure) with an empty string. Generics are arranged in the same order as the
corresponding dimensions are defined in the template. Consider the example from the beginning of
this document:
```java
/* with char|byte|short|int|long|float|double|object key
        int|byte|char|short|long|float|double|object value */
/* if !(object key object value) */

interface CharIntPair/*<>*/ extends Pair<Character, Integer> {
  ...
}
```

Here are the lines that JPSG will produce for different specializations:
 - `ByteBytePair extends Pair<Byte, Byte>`
 - `ObjectBytePair<K> extends Pair<K, Byte>`
 - `ByteObjectPair<V> extends Pair<Byte, V>`
 - `ObjectObjectPair<K, V> extends Pair<K, V>` - not actually generated in this particular example
 because of the exclusive condition in the beginning of the file, but could have been generated if
 the condition wasn't there.

`/*<extends>*/`, `/*<super>*/`, `/*<?>*/` follow the same rules, but they are replaced with
`<? extends T, ? extends U, ...>`, `<? super U, ? super T, ...>` and `<?, ?, ...>` respectively. `T`
and `U` are just examples - JPSG derives the letters from the dimension names, see the section about
[specialization of Java type dimensions](#specialization-of-java-type-dimensions).

If there is a single-value dimension called "view" in the context and its value corresponds to the
name of some Java type dimension in the context, generics structures consider only that dimension.
This feature is useful in a specific case of Map-like templates with KeySet and Values views, for
example:
```java
/* with byte|short|char|int|long|float|double|object key
        short|byte|char|int|long|float|double|object value */

class MyByteShortMap/*<>*/ implements Map/*<>*/ {
  ...
  /* with key view */
  class KeySetView implements ByteSet/*<>*/ {
    ...
  }
  /* endwith */

  /* with value view */
  class ValuesView implements ShortCollection/*<>*/ {
    ...
  }
  /* endwith */
}
```

is specialized to code like the following when "key=object" and "value=object":
```java
class MyObjectObjectMap<K, V> implements Map<K, V> {
  ...

  class KeySetView implements ObjectSet<K> {
    ...
  }

  class ValuesView implements ObjectCollection<V> {
    ...
  }
}
```
Note that JPSG replaces `/*<>*/` with `<K>` rather than `<K, V>` in the line
`class KeySetView implements ObjectSet/*<>*/ {` and with `<V>` in the line
`class ValuesView implements ObjectCollection/*<>*/ {` because of the "view" dimensions specified.

#### `/* raw */` modifier

The `/* raw */` modifier was already mentioned in the [section about specialization of Java type
dimensions above](#specialization-of-java-type-dimensions), it tells JPSG to replace a source
primitive type occurrence with `Object` rather than a generic parameter reference.

#### `/* bits */` modifier and floating wrapping

In some cases, when specializing to `float` and `double` types you may need to refer to `int` and
`long` primitive types as the "bits" counterparts of `float` and `double` (`int` and `long` are the
return types of `Float.floatToIntBits()` and `Double.doubleToLongBits()`, respectively). To achieve
that a standalone primitive occurrence should be prepended with a `/* bits */` modifier, for
example:
```java
/* with byte|int|long|float|double t */

/* bits */byte unwrapByte(byte v) { return /* unwrap t */v; }
/* bits */byte unwrapRawByte(byte v) { return /* unwrapRaw t */v; }
byte wrapByte(/* bits*/byte bits) { return /* wrap t*/bits; }

/* endwith */
```

JPSG generates code like the following from the above template:
```java
byte unwrapByte(byte v) { return v; }
byte unwrapRawByte(byte v) { return v; }
byte wrapByte(byte bits) { return bits; }

int unwrapInt(int v) { return v; }
int unwrapRawInt(int v) { return v; }
int wrapInt(int bits) { return bits; }

long unwrapLong(long v) { return v; }
long unwrapRawLong(long v) { return v; }
long wrapLong(long bits) { return bits; }

int unwrapFloat(float v) { return Float.floatToIntBits(v); }
int unwrapRawFloat(float v) { return Float.floatToRawIntBits(v); }
float wrapFloat(int bits) { return Float.intBitsToFloat(bits); }

long unwrapDouble(double v) { return Double.doubleToLongBits(v); }
long unwrapRawDouble(double v) { return Double.doubleToRawLongBits(v); }
double wrapDouble(long bits) { return Double.longBitsToDouble(bits); }
```

This example presents auxiliary floating point wrapping/unwrapping structures which are often useful
at the same time as you need to reference the `/* bits */` types: `/* wrap <dimension-name> */`,
`/* unwrap <dimension-name> */`, and `/* unwrapRaw <dimension-name> */` are replaced along with the
immediately following alphanumeric word (`v` and `bits` in the example above) with
`Float.intBitsToFloat(<alphanumeric-arg>)`, `Float.floatToIntBits(<alphanumeric-arg>)`, and
`Float.floatToRawIntBits(<alphanumeric-arg>)` respectively, if the referenced dimension specializes
to `float` in the current context, with the corresponding methods in `Double` class if the
referenced dimension specializes to `double` in the current context, or simply with
`<alphanumeric-arg>` (in other words, it is left intact) if the referenced dimension specializes to
neither `float` nor `double`.

#### Template-level comments

As mentioned in the section about [`/* define */ blocks`](#-define--blocks), you can write something
that won't appear in the JPSG's output code using `/* comment */` definition:
```java
//comment// Explain something about the following generation structures /**/
/* if ... */
```

#### A/An: insert correct English indefinite articles

JPSG replaces `//a//` and `//an//` (as well as `/*a*/`, `//a*/` and `/*a//`, and all equivalent
structures with "an", as usual for JPSG structures) with "a" or "an" depending on whether the next
word starts with a vowel letter: "a", "e", "i", "o" or "u". For example, JPSG translates the
following template:
```java
/* with byte|char|int t */
/** Returns //a// byte value. */
byte getByte();
/* endwith */

/* with Apple|Orange|Banana fruit */
/** Returns //an// {@link Apple}. */
Apple getApple();
/* endwith */
```
into
```java
/** Returns a byte value. */
byte getByte();
/** Returns a char value. */
char getChar();
/** Returns an int value. */
int getInt();

/** Returns an {@link Apple}. */
Apple getApple();
/** Returns an {@link Orange}. */
Orange getOrange();
/** Returns a {@link Banana}. */
Banana getBanana();
```

However, JPSG is *not* sophisticated enough to follow the actual English grammar rules that are
based on sounds, not letters (consider "an hour", "a union").

As this structure is usually used in Javadoc comments specifically in expressions like "a char
variable", it recognizes Javadoc's `@code` and `@link` tags following the indefinite article and
specializes `//a// {@code char} variable` and `//an// {@link Apple}` (as you can see in the above
example) correctly as well.

#### Print the current option for a dimension

JPSG replaces structures `/* print <dimension-name> */ ... /* endprint */` with the option for the
mentioned dimension in the current generation context. It doesn't make a lot of sense when the
dimension is defined using a `/* with */` structure within the template file, because you can just
write the source option directly (that is, employ the main function of JPSG). However, `/* print */`
may be handy when the dimension is provided externally via a [Gradle plugin](#gradle-plugin) (using
[`with()` method](#methods)) or a [Maven plugin](
https://github.com/TimeAndSpaceIO/jpsg-maven-plugin) configuration.

## Gradle Plugin

The JPSG Gradle plugin is built after the Gradle's core [ANTLR plugin](
https://docs.gradle.org/5.3/userguide/antlr_plugin.html) and creates a similar set of tasks and
dependencies.

### Usage

The plugin depends on the Java Gradle plugin. To use the JPSG Gradle plugin, include the following
in your build script:
```groovy
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'io.timeandspace:jpsg-gradle-plugin:1.4'
    }
}

plugins {
  id 'java'
}

apply plugin: 'jpsg'
```
This is Groovy syntax. For Kotlin syntax, see [an example in Gradle docs](
https://docs.gradle.org/5.3/userguide/plugins.html#sec:applying_plugins_buildscript).

You can find a full working example in [`jpsg-gradle-plugin-test`](jpsg-gradle-plugin-test) project.

### Tasks

The JPSG Gradle plugin adds the following tasks to your project:

 - `generateJavaSpecializations` - [`JpsgTask`](#jpsgtask-configuration) <br>
 &nbsp;&nbsp;&nbsp;&nbsp;Generates source code from production templates (stored in directory
 `src/main/javaTemplates/`).
 - `generateResourceSpecializations` - [`JpsgTask`](#jpsgtask-configuration) <br>
 &nbsp;&nbsp;&nbsp;&nbsp;Generates resource files from production resource templates (stored in
 directory `src/main/resourceTemplates/`).
 - `generateTestJavaSpecializations` - [`JpsgTask`](#jpsgtask-configuration) <br>
 &nbsp;&nbsp;&nbsp;&nbsp;Generates source code from test templates (stored in directory
 `src/test/javaTemplates/`).
 - `generateTestResourceSpecializations` - [`JpsgTask`](#jpsgtask-configuration) <br>
 &nbsp;&nbsp;&nbsp;&nbsp;Generates resource files from test resource templates (stored in directory
     `src/test/resourceTemplates/`).
 - <code>generate<i>SourceSet</i>JavaSpecializations</code> - [`JpsgTask`](#jpsgtask-configuration) <br>
 &nbsp;&nbsp;&nbsp;&nbsp;Generates source code from the templates for the given source set (stored
 in directory <code>src/<i>sourceSet</i>/javaTemplates/</code>).
 - <code>generate<i>SourceSet</i>ResourceSpecializations</code> - [`JpsgTask`](#jpsgtask-configuration) <br>
 &nbsp;&nbsp;&nbsp;&nbsp;Generates resource files from the resource templates for the given source
 set (stored in directory <code>src/<i>sourceSet</i>/resourceTemplates/</code>).

The plugin adds the following dependencies to tasks added by the Java plugin:

Task name                                     | Depends on
--------------------------------------------- | ----------
`compileJava`                                 | `generateJavaSpecializations`
`processResources`                            | `generateResourceSpecializations`
`compileTestJava`                             | `generateTestJavaSpecializations`
`processTestResources`                        | `generateTestResourceSpecializations`
<code>compile<i>SourceSet</i>Java</code>      | <code>generate<i>SourceSet</i>JavaSpecializations</code>
<code>process<i>SourceSet</i>Resources</code> | <code>generate<i>SourceSet</i>ResourceSpecializations</code>

### Directory layout

<code>src/<i>sourceSet</i>/javaTemplates/</code> (including `main` and `test` source sets) - JPSG
treat all files in this directory (including all files in subdirectories, recursively) as templates
for generation of source code in the given source set. Template files should be organized in
subdirectories repeating the package structure as well as for normal Java sources.

<code>src/<i>sourceSet</i>/resourceTemplates/</code> - JPSG treats all files in this directory as
templates for generation of resource files in the given source set.

### `JpsgTask` configuration

The fully qualified class name of JPSG tasks is `io.timeandspace.jpsg.JpsgTask`. So, to configure
all tasks added by the JPSG Gradle plugin, you can write:
```groovy
tasks.withType(io.timeandspace.jpsg.JpsgTask) {
  defaultTypes = "int|long|float|double"
  exclude "float|double t int|long|float|double u"
}
```

Alternatively, you can configure specific JPSG tasks:
```groovy
configure([generateJavaSpecializations, generateTestJavaSpecializations]) {
  with "Enabled extraChecks"
}
``` 

#### Properties
##### `String defaultTypes`
For all template files without `/* with */` blocks in the beginning JPSG attempts to deduce
dimensions from the name of the template file, taking possible options from this `defaultTypes`
configuration. See [more details](#with-default-types) in the section about `/* with */` blocks.

Format: [`<javaTypeOptions>`](#dimensions-bnf).

Default value: `byte|char|short|int|long|float|double`.

##### `File source`
The source directory which JPSG traverses and considers all files in it as template files.

Default value: <code>src/<i>sourceSet</i>/javaTemplates/</code> or
<code>src/<i>sourceSet</i>/resourceTemplates/</code>, see the [directory layout](#directory-layout).

##### `File target`
The target directory where JPSG puts specialized sources.

Default value: `${project.buildDir}/generated-src/jpsg/${sourceSet.name}`.

#### Methods
##### `never(String... options)`
For all dimensions defined in the beginnings of template files in `/* with */` blocks, or deduced
automatically by JPSG (see `defaultTypes` property above), or defined inside template files at any
level of nesting, JPSG will skip generating code for the specified options. `never()` may be called
several times, adding more options to skip.

`options` parameter format: [`<options>`](#dimensions-bnf).

Example:
```groovy
never("byte|short", "char") // Never generate code for byte, short, char types
never "Assert" // Additionally, don't generate code for non-Java type `Assert` option
```

##### `exclude(String... conditions)`
JPSG doesn't generate specialization files for dimension contexts (either determined by `/* with */`
blocks in the beginnings of the template files, or deduced using `defaultTypes`) that match any of
the conditions passed to `exclude()`. In other words, each exclusion condition is virtually added to
[an `/* if */` block in the beginning of each template file](#template-file-beginning-if) like the
following:
```java
/* with <some explicitly defined dimensions (may be absent)> */
/* if !(exclusionCondition1) && ... && !(exclusionConditionN) */
/* if <some explicit, file-specific condition (may be absent) > */
... The rest of the template file
```
While conditions in explicit `/* if */` blocks can only reference the existing (defined or deduced)
dimensions, exclusion conditions specified via `exclude()` may reference some dimensions that are
not present in every template file. Such exclusion conditions don't match and therefore don't make
JPSG to skip any context specialization for the template files which don't define some dimensions
from these exclusion conditions.

Semantics of conditions are described in detain in the section about [`/* if */` blocks
](#-if--blocks).

Exclusion conditions are useful when you want to suppress generation for some combinations of
dimension options consistently in all template files. `exclude()` allows to not write the same
`/* if */` block in the beginning of each template file.

`conditions` parameter format: [`<simple-condition>`](#conditions-bnf).

Example:
```groovy
exclude("object key byte|short|char|object value", "byte key short|char value")
exclude "Disabled extraChecks Enabled advancedStatistics"
```

##### `with(String... dimensions)`
JPSG adds the provided dimensions to the generation contexts in each template file. Each dimension
must have a single option.
 
Note that JPSG adds the provided dimensions to the context of a template file after applying the
exclusion conditions (specified via `exclude()`) and before filtering specializations with the
explicit `/* if */` block in the beginning of the template file (if present). So the  beginning of
each template file virtually looks like the following:
```java
/* with <some explicitly defined dimensions (may be absent)> */
/* if !(exclusionCondition1) && ... && !(exclusionConditionN) */
/* with providedDimension1 ... providedDimensionM */
/* if <some explicit, file-specific condition (may be absent) > */
... The rest of the template file
```

`with()` allows to enrich the generation contexts in all template files without adding them in to
the `/* with */` block in the beginning of each template file.
  
`with()` dimension may also be used in conjunction with [`/* print */` structures
](#print-the-current-option-for-a-dimension) to "macro print" something in template files during
generation.
 
`dimensions` parameter format: [`<dimensions>`](#dimensions-bnf). Every dimension must have only a
single option.
 
Example:
```groovy
with("Enabled extraChecks Disabled advancedStatistics", "Assert extraCheckStyle")
with "java8 minSupportedJavaVersion"
```
