# Builds the app and runs lint plus the unit tests without installing anything on the host:
#   docker build -t showdown-ds .
#   docker run --rm showdown-ds                      # ./build.sh --test
#   docker run --rm -v "$PWD/out:/out" showdown-ds sh -c './build.sh && cp app/build/outputs/apk/debug/*.apk /out/'
FROM eclipse-temurin:17-jdk

ARG ANDROID_CMDLINE_TOOLS=11076708
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${PATH}:/opt/android-sdk/cmdline-tools/latest/bin"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"

WORKDIR /workspace
COPY . .

CMD ["./build.sh", "--test"]
