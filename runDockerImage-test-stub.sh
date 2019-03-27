docker run -it                         \
           --rm                        \
           --volume $(pwd):/dpnt-video \
           --workdir /dpnt-video       \
           -p 9000:9000                \
           -p 9324:9324                \
           -p 9988:9988                \
           python37-dpnt-video:0.1     \
           /dpnt-video/startExternalDependencies.sh