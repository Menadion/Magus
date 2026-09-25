# Mogar

A small Android app that shows a family on one map.

Everyone installs it from a file, joins a family with a six-letter code, and sees each other
as a dot on the map, with a name, when they were last seen, and their battery. It is built for
parents who are not phone people: one map, one switch, nothing to learn.

## What it does

- **One family, one code.** Someone starts a family and gets a code. Everyone else types the
  code once.
- **A dot per person.** Green while sharing, grey when someone switched sharing off, hollow
  when their phone has gone quiet for fifteen minutes. Tap a dot for last seen and battery.
- **Sharing in the background.** Locations go up every five minutes, with a permanent
  notification so sharing is always visible. A phone restart brings it back on its own.
- **Keep Mogar running.** Xiaomi, Vivo and Samsung close background apps to save battery.
  A one-time screen walks through the settings that stop that, per brand.
- **Profiles.** A name and a small picture, shown on the dot. A phone number if you want,
  so the family can call or text you from your card.
- **Light and dark.**

## What it stores

Only what the map needs: a name, a family code, the latest location (overwritten each time,
no history), battery, last seen, an optional picture shrunk to 128 pixels, and an optional
phone number seen only by the family. Sign-in is anonymous. Nothing else, no emails. Data lives in a Firebase project owned
by the family that runs the app.

## Installing

There is no Play Store listing. Download `Mogar.apk` from the latest release, open it from
the Files app, and allow the install. Updates install over the old version and keep your
family and settings.

## Building it yourself

You need Android Studio and a Firebase project of your own.

1. In Firebase, add an Android app with the package name `io.github.menadion.magus`, enable
   anonymous sign-in and Firestore, and paste `firestore.rules` into the Firestore rules.
2. Put the project's `google-services.json` in `app/`.
3. For a release build, make a signing key and point `keystore.properties` at it
   (see `app/build.gradle.kts` for the four fields). Debug builds need no key.
4. Build:

   ```
   ./gradlew assembleRelease
   ```

The map tiles come from [OpenFreeMap](https://openfreemap.org), drawn with
[MapLibre](https://maplibre.org), on [OpenStreetMap](https://www.openstreetmap.org/copyright)
data. The font is [Figtree](https://github.com/erikdkennedy/figtree), bundled under the
SIL Open Font License.

## Status

Version 0.2. Kotlin and Jetpack Compose, Android 8 and up.
