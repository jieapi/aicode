FROM gitpod/workspace-base:latest

USER gitpod

ENV ANDROID_HOME=/home/gitpod/android-sdk
ENV ANDROID_SDK_ROOT=/home/gitpod/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline-tools.zip && \
    unzip -q cmdline-tools.zip && \
    mv cmdline-tools latest && \
    rm cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true

RUN sdkmanager \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0"

RUN sudo apt-get update && \
    sudo apt-get install -y openjdk-17-jdk && \
    sudo update-java-alternatives -s java-1.17.0-openjdk-amd64 && \
    sudo rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
