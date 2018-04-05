all: out/artifacts/Assignment_2_jar/Assignment\ 2.jar

out/artifacts/Assignment_2_jar/Assignment\ 2.jar: build.xml src/*
	ant

clean:
	rm -rf out
