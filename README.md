#### User documentation

The complete user docs are available at [launcher.gigahex.com](https://launcher.gigahex.com)

#### Developer Environment setup

This project uses `sbt` as the build tool. It consists of 3 modules.
 
- **core** - Consists of launcher datastructure that can be extended to add support for different infrastructure
services
- **tool** - The command line tool, that parses `yaml` dsl to build the set of tasks, and executes the same.
- **aws** - Adds support for S3 and EMR as of now.

##### Installation and packaging
We use `sbt-pack` plugin to create the package. Following is the command to create the package.
For creating archive file, use `sbt packArchive` and for creating artifacts without packaging, use `sbt pack`.
