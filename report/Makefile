SOURCES = $(shell find . -regextype posix-egrep -regex "\./[[:digit:]]+.*.md" | sort -n)

BIB = bibliography.bib
HEADER = header.tex

report.pdf: $(SOURCES) $(BIB) $(HEADER) appendix.tex images
	pandoc -o $@ --number-sections --bibliography=$(BIB) \
		--include-in-header=$(HEADER) $(SOURCES) \
		--include-after-body=appendix.tex --latex-engine=xelatex

appendix.tex: appendix.md
	pandoc -o $@ $^

clean:
	rm report.pdf
