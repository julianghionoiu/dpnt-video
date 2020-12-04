FROM buildpack-deps:stretch-scm

# Install python 3.7 by copy installed layers from another image
COPY --from=python:3.7 / /

RUN python3 --version
RUN pip --version

# Install Java 8
COPY --from=openjdk:8u275-jdk /usr/local/openjdk-8 /usr/local/openjdk-8

ENV JAVA_HOME=/usr/local/openjdk-8
ENV PATH=${JAVA_HOME}/bin:${PATH}

RUN echo "JAVA_HOME=${JAVA_HOME}"
RUN echo "PATH=${PATH}"

RUN rm -fr /var/lib/apt/lists/*

RUN apt-get install --fix-missing && apt-get update && apt-get install -qy \
    libpython-dev \
    groff-base    \
    git           \
    libxml2-utils \
    jq            \
    dos2unix      \
    ffmpeg        \
    --no-install-recommends && rm -r /var/lib/apt/lists/*
RUN pip install awscli

ENV WORKDIR /srv
WORKDIR ${WORKDIR}

ENV INTEROP_QUEUE_VERSION 0.1.18
RUN wget https://github.com/julianghionoiu/tdl-interop-event-queue/releases/download/v${INTEROP_QUEUE_VERSION}/queue-cli-tool-${INTEROP_QUEUE_VERSION}-all.jar \
    -O ${WORKDIR}/queue-cli-tool-all.jar

COPY download_and_merge_video.sh ${WORKDIR}

ENTRYPOINT ${WORKDIR}/download_and_merge_video.sh
