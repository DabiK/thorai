// Les enregistrements du pont (ticket 02), sans code : la passerelle factice les rejoue.
// Lus là où ils sont rangés, sans copie, et rangés dans le jar sous `bridge-ws/`. Seuls le debug
// de `:app` et les tests de `:gateway-bridge` en dépendent : ils ne partent pas dans l'APK release.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        resources {
            srcDir(rootProject.layout.projectDirectory.dir("../bridge/fixtures"))
            include("bridge-ws/*.jsonl")
        }
    }
}
