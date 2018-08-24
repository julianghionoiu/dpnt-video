FROM java:8u111-jdk
RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java

RUN apt-get update && apt-get install -qy \
    python-pip \
    groff-base \
    git \
    libxml2-utils \
    jq \
    dos2unix \
    --no-install-recommends && rm -r /var/lib/apt/lists/*
RUN pip install awscli

ENV WORKDIR /srv
WORKDIR ${WORKDIR}

ENV INTEROP_QUEUE_VERSION 0.1.18
RUN wget https://github.com/julianghionoiu/tdl-interop-event-queue/releases/download/v${INTEROP_QUEUE_VERSION}/queue-cli-tool-${INTEROP_QUEUE_VERSION}-all.jar \
    -O ${WORKDIR}/queue-cli-tool-all.jar

COPY download_and_merge_videos.sh ${WORKDIR}

ENTRYPOINT ${WORKDIR}/download_and_merge_videos.sh
