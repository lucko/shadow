# shadow
[![Javadocs](https://javadoc.io/badge/me.lucko/shadow.svg)](https://javadoc.io/doc/me.lucko/shadow) [![Maven Central](https://img.shields.io/maven-metadata/v/https/repo1.maven.org/maven2/me/lucko/shadow/maven-metadata.xml.svg?label=maven%20central)](https://search.maven.org/artifact/me.lucko/shadow)

An annotation based API for Java reflection.

The system was inspired by the [`Shadow`](http://jenkins.liteloader.com/job/Mixin/javadoc/org/spongepowered/asm/mixin/Shadow.html) feature in the SpongePowered [Mixin](https://github.com/SpongePowered/Mixin) library. The code in this repository is adapted from the package previously built into [lucko/helper](https://github.com/lucko/helper).

### Example
Given the following example base class:

```java
public class Person {
    private final String name;
    private final int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return this.name;
    }

    public int getAge() {
        return this.age;
    }
}
```

Let's assume we want to increment the `Person`s age on their birthday. The class is immutable, and doesn't allow us to modify the age once constructed - so, we need to use reflection to change the value of the field.

#### Normal Java Reflection
This can be done using plain old reflection like this.

```java
public static void incrementAge(Person person) {
    Field ageField;
    try {
        ageField = Person.class.getDeclaredField("age");
    } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
    }

    ageField.setAccessible(true);

    try {
        ageField.setInt(person, ageField.getInt(person) + 1);
    } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
    }
}
```

#### Shadow
However, with shadow, our approach is slightly different.

We start by defining a "shadow interface" for the `Person` class.

```java
@ClassTarget(Person.class)
public interface PersonShadow extends Shadow {

    int getAge();

    @Field
    void setAge(int age);

    default void incrementAge() {
        setAge(getAge() + 1);
    }
}
```

The `getAge` method simply mirrors the existing method defined on the Person class - nothing special going on there. However, the `setAge` method is bound to the `age` field.

Once the shadow interface has been defined, we can use the `ShadowFactory` to obtain a "shadow" instance for our person.

The `incrementAge` method can then be implemented as follows.

```java
public static void incrementAge(Person person) {
    PersonShadow personShadow = ShadowFactory.global().shadow(PersonShadow.class, person);
    personShadow.incrementAge();
}
```

The shadow approach has a number of key advantages over the plain reflection method.

* The structure of the `Person` class is outlined in one central location - the shadow interface.
    * If the layout of `Person` changes - we only have to update one obvious place.
    * The places in our program using the shadow (in this case the `incrementAge` method) aren't cluttered with the details of the person class.
* We don't have to deal with the checked exceptions associated with obtaining the field or modifying the value. These are simply wrapped up into a `RuntimeException` thrown when the shadow is obtained.
* The shadow implementation caches the underlying `Field`, `Method` etc instances behind the scenes, we don't have to worry!
