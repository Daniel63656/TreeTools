# TreeTools

This project is a **Git-like version control system** for Java data models, enabling users to automatically track changes
across different copies of the same model. This allows **thread-safe manipulation** as well as offering
a range of other features related to tracking, saving and restoring states of a data model.  
The system leverages **reflections** and **aspect-oriented programming (AOP)** to allow these features to work for **arbitrary**
data models defined within the provided framework, with only minor restrictions. Once tracking is enabled for a defined
data model, any changes made to the model are detected and tracked automatically, with no explicit logging or function
calling required.

## Features

- Automatic tracking and synchronization of changes across different workcopies of a data model using operations like
  **commit** and **pull**
- Ability to save states and use **redo/undo** operations
- Automatic cloning (deep copying) of the data model
- JSON serialization and deserialization
- Wrapper mechanism to attach decorator objects to data model classes, with the ability to get notified when the underlying object changes or is removed

All these features are available while allowing the creation of highly complex data models including the use of:
- Abstract classes
- Weak and circular references
- Polymorphic parents (classes that can have different owner types depending on its location in the model structure)
- Arbitrary data types (including custom immutable objects)
- Collections, Maps, Arrays and direct references to other data model classes (Lists not possible)
- Usage of data model classes as map keys

Limitations:
- Object's lifetimes are coupled to owner
- container only can hold MutableObjects
- No lists

## Getting Started

TODO add aspect plugin to data model?!

To use TreeTools in your project, the easiest way is to build it as a `mavenLocal`
artifact and import it.

1. Clone the TreeTools repository:
    ```bash
    git clone https://github.com/Daniel63656/TreeTools.git
    ```

2. Navigate to the project directory and run the publishToMavenLocal gradle task:
    ```bash
    cd ArpackJ
    ./gradlew publishToMavenLocal
    ```
3. In your project’s `build.gradle` file, add the following lines:
    ```gradle
    repositories {
        mavenLocal()
    }
   
    dependencies {
        implementation group: 'net.scoreworks', name: 'TreeTools', version: '1.1.0'
    }
    ```

## Usage

see tutorial

### Engage Transactions

Changes made to a data model are not tracked by default. This enables the user to use transactions (that come
with some overhead), only where needed. To enable transactions for your data model, get an instance of the global
`TransactionManager` and enable transactions for your data model:
```java
TransactionManager tm = TransactionManager.getInstance();
tm.enableTransactionsForRootEntity(originalModelRootInstance);
```
Under the hood, the system will now create a `Repository` for your data model's `RootEntity`. A repository acts as a wrapper
that connects your data model with a special two-way map, referred to as `Remote`, that connects each data model object
to an immutable object state that is detached from actual object changes.
Any local changes made to the data model are tracked by the remote. Only when `commit()` is called, these local changes
are packaged as `Commit` and the `Remote` gets updated.

![Data model classes](docs/structure.png)

> **Note:**
> Engaging transactions will index the classes of your data model once using reflections. If your data model violates the
> data model requirements of this system, an `IllegalDataModelException` will be thrown. Indexing also happens when using
> other features, such as JSON serialization.

Now that transactions are properly set up, we can use the system to create a deep copy of the original data model. This is
particularly useful for enabling another thread to read the copy—for example, for a view—while allowing the original data
model to be modified concurrently. We obtain such a copy by using the `clone()` method. This will create a separate 
data model inclusive separate `Repository` and `Remote`.
```java
workcopyRoot = (RootClass) tm.clone(originalModelRootInstance);
```

Now we can operate on the original model, while the workcopy can be safely accessed. After some changes are made, they
can be committed to the remote. Our workcopy is still not modified by this.
```java
originalModelRootInstance.commit();
```

To synchronize the workcopy with the recently committed changes, our reading thread should call `workcopyRoot.pull()`
at regular intervals (e.g. before drawing). This will fetch available commits from the `TransactionManager` and apply the changes to the workcopy.
Congratulations, the workcopy is now up to date with the original data model!

### Undos and Redos

We can use the `TransactionManager` again to enable the usage of undos/redos:
```java
tm.enableUndoRedos();
```

Now you can archive a version of the data model by calling `tm.createUndoState()`. Under the hood, the `TransactionManager`
will keep a squash commit around, that captures all the commited changes since the last undo state (or beginning of transactions).
Use `originalModelRootInstance.undo()` and `originalModelRootInstance.redo()` to visit these preserved states.  
Changes introduced to the original data model by undo/redo can be pulled by workcopies like any other commit.

### Disengage Transactions

If you want to stop using transactions you should call `tm.shutdown()` to properly remove all references to workcopies
and commits.

### JSON parsing


This project also supports the serialization and deserialization of your data model, enabling you to save it externally,
such as in a database. This functionality can be used independently of the transaction system.
To create a JSON string from your data model, simply call:
```java
String json = JsonParser.toJson(fullScore, true);   // Using pretty printing
```
To load a data model from a json string, use the `JsonParser`:
```java
RootClass root = JsonParser.fromJson(json, RootClass.class);
```
