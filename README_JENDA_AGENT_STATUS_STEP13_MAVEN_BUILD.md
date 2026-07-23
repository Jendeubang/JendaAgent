# Step 13 Maven Build Environment

## Problem

The globally configured Maven local repository is under the Maven installation
directory and is not writable. Its cached metadata also references unavailable
repositories, which prevents recompilation after source changes.

## Project-local Build Command

Use the checked-in settings file and a writable project-local repository:

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -s F:\JendaAgent\JendaAgent\tools\maven-central-settings.xml `
  -Dmaven.repo.local=F:\JendaAgent\.m2-central `
  -DskipTests compile
```

The first build downloads Maven dependencies. Subsequent builds reuse
`F:\JendaAgent\.m2-central` and do not use the inaccessible global cache.
