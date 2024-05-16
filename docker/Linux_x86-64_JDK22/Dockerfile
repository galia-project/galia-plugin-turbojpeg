FROM rockylinux:9

ARG github_pat
ENV GITHUB_PAT=$github_pat
ENV JAVA_HOME=/opt/jdk
ENV PATH=/opt/jdk/bin:/opt/maven/bin:$PATH

# Install a couple of dependencies, except maven and java, because the yum
# versions are old and will bring in tons of other stuff.
# Also, libjpeg-turbo is too old, so we'll obtain it separately.
RUN yum install -y \
    git \
    wget

# Install libjpeg-turbo, a JDK, & Maven
RUN wget -q https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/3.0.3/libjpeg-turbo-official-3.0.3.x86_64.rpm \
    && dnf install -y ./libjpeg-turbo-official-3.0.3.x86_64.rpm \
    && rm libjpeg-turbo-official-3.0.3.x86_64.rpm \
    && wget -q https://download.java.net/java/GA/jdk22/830ec9fcccef480bb3e73fb7ecafe059/36/GPL/openjdk-22_linux-x64_bin.tar.gz \
    && tar xfz openjdk-22_linux-x64_bin.tar.gz \
    && mv jdk-22 $JAVA_HOME \
    && wget -q https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz \
    && tar xfz apache-maven-3.9.6-bin.tar.gz \
    && mv apache-maven-3.9.6 /opt/maven

ARG user=galia
ARG home=/home/$user
RUN adduser --home $home $user && chown -R $user $home
USER $user
WORKDIR $home

# Copy plugin code
COPY --chown=galia . .

ENTRYPOINT exec docker/Linux_x86-64_JDK22/test.sh $GITHUB_PAT
