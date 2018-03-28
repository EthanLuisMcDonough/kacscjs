# kacscjs
A contest judging system for the @Khan Academy Computer Programming section

### Testing the app locally
1. Make sure you have [Node](https://nodejs.org/en/download/), Java, and [SBT](https://www.scala-sbt.org/1.0/docs/Setup.html) installed 
2. `git clone https://github.com/EthanLuisMcDonough/kacscjs.git`
3. `cd kacscjs`
4. `npm install`
5. `npm run build`
6. Open SBT by running `sbt`
7. `playGenerateSecret`
8. Open conf/application.conf
 and set `play.http.secret.key` to the generated secret's value
9. Make a copy of app/configs/PrivateConfigTemplate.java and fill out the template.  Rename this new file PrivateConfig.java
10. `run` to run the app in dev mode
