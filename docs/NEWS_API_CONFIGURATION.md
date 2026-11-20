# NewsAPI configuration and exposed-key response

## Build behavior

NewsAPI is optional. A debug build without `NEWS_API_KEY` succeeds, and the news
search reports that NewsAPI is not configured instead of fabricating or silently
substituting articles.

For local testing only, supply a personal, restricted NewsAPI key through the
`NEWS_API_KEY` environment variable or as a `NEWS_API_KEY` entry in the
git-ignored `local.properties`, then run:

```sh
bash scripts/android-build.sh assembleDebug
```

The key is included in that debug APK so the app can call NewsAPI directly.
Do not distribute an APK built this way. Release APKs are built with an empty
NewsAPI key regardless of local debug configuration.

## APK credentials are not secrets

Anything compiled into an Android APK, including a BuildConfig field, can be
recovered by someone who obtains the APK. Obfuscation, app signing, and putting
the original value in a Replit Secret, environment variable, or untracked local
file do not change that: they only protect the value before it is compiled.
The NewsAPI request can also be observed on a device controlled by its user.

For a distributed app, keep the NewsAPI credential on a backend service and have
the app call that service. The backend should authenticate app users, restrict
and rate-limit requests, and avoid returning the upstream credential. This
repository does not currently provide that proxy, so release builds do not
enable direct NewsAPI access.

## Exposed credential

A NewsAPI credential was previously present in public app build configuration.
Removing it from the current source does not remove it from repository history
or previously built APKs. The NewsAPI account owner should revoke/rotate that
credential immediately, review account usage, and avoid reusing it. Do not put
the replacement credential in this repository or in a distributable APK.
