# Maverick
**Maverick** is a software that helps you combine releases in projects with multiple modules, change lists and artifacts. 
The program interface is command line based and relatively easy to use, provided the exported project is set up 
correctly.

## Background / Use Case
I created this software to speed up **Utopia** project Git releases. Since the project has over a large number of 
modules and separate change list documents, it takes a while to collect data manually, even when using an IDE.

I'm using IntelliJ IDEA IDE in Utopia development, so the design choices in this software also reflect that. 
For example, I expect there to be a directory named out/artifacts, and I expect the project to be directory-based, 
at least when it comes to modules.

## What it does exactly?
**Maverick** performs the following tasks in your stead:
- Makes sure you have included an artifact for each module
- Identifies, based on change list documents, which modules changed and which didn't
- Checks if there are any modules that maybe should be listed as new versions, based on exported jar file sizes
- Makes sure you have included a release summary in all updated modules
- Writes a changes summary document, combining individual change list entries and also listing all 
  current module versions
  - In case of patches, where only a single module is updated, doesn't compile a summary but simply uses that module's 
    change document
- Appends the correct version number after each exported jar file and collects them to a single directory

So, you're left to deal with these tasks:
- Version control tasks (merging to master, creating a tag, etc.)
- Before using **Maverick**, build all artifacts
- After using **Maverick**, creating a Git release where you copy the change summary and attach the collected files

## Target Project Requirements
- The targeted projects **must** consist of one or more modules, which appear in the file system as 
  **directories directly below** the main project directory.
- Each module **should** contain a **Changes.md** -file where it lists changes per version.
  - Other file names are also supported as long as they contain the word "change" (case-insensitive).
- Change documents **must** list the versions from latest to earliest (if they contain multiple versions). 
- Each change document version header **must** start with `#` and contain a version number (e.g. v1.2.3).
- Modules **should** have an artifact directory that correlates with their name.
- The artifact directories **must** be located at `<project root>/out/artifacts`.
- In case of single jar file exports, artifact jar names **should** correlate with module names
  - Other cases are considered application exports and are handled differently

## Usage instructions
**Maverick** is a Scala-based application, so it needs a jre (8 or later) in order to run.

Like mentioned above, before launching **Maverick**, you should have built all artifacts in IntelliJ IDEA or other 
such IDE.

Launch **Maverick** from command line (in the same directory where the jar file is) with `java -jar Maverick.jar`

After this, you simply need to answer the questions the application presents to you.

When the process is complete, **Maverick** will open the generated directory for you, from which you can copy 
information to your Git release (or other release).

During the program use, you can terminate the process at any time by writing `exit` as input.

## Internal Dependencies
Besides Scala and Java 8+, **Maverick** depends on **Utopia Flow**.