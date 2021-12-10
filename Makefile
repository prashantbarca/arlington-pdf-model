#
# Copyright 2021 PDF Association, Inc. https://www.pdfa.org
#
# This material is based upon work supported by the Defense Advanced
# Research Projects Agency (DARPA) under Contract No. HR001119C0079.
# Any opinions, findings and conclusions or recommendations expressed
# in this material are those of the author(s) and do not necessarily
# reflect the views of the Defense Advanced Research Projects Agency
# (DARPA). Approved for public release.
#
# SPDX-License-Identifier: Apache-2.0
# Contributors: Peter Wyatt, PDF Association
#
# A simple makefile to automate some repetitive tasks. Typical usage:
# - make clean
# - make 3d
# - make pandas
# - make tsv > tsv.out
# - make xml > xml.out
# - make validate
#

XMLLINT ::= xmllint
XMLLINT_FLAGS ::= --noout

# Clean up all outputs that can be re-created
.PHONY: clean
clean:
	rm -rf ./3dvisualize/*.json
	rm -rf ./xml/*.xml
	rm -rf ./scripts/*.tsv
	rm -rf ./tsv/1.0/*.tsv ./tsv/1.1/*.tsv ./tsv/1.2/*.tsv ./tsv/1.3/*.tsv ./tsv/1.4/*.tsv
	rm -rf ./tsv/1.5/*.tsv ./tsv/1.6/*.tsv ./tsv/1.7/*.tsv ./tsv/2.0/*.tsv
	rm -rf ./gcxml/dist/gcxml.jar
	rm -rf ./TestGrammar/bin/x64/TestGrammar*.* ./TestGrammar/bin/x86/TestGrammar*.*
	rm -rf ./TestGrammar/bin/linux/TestGrammar* ./TestGrammar/bin/darwin/TestGrammar*


# Make the monolithic TSV file by combining all TSVs - suitable for Jupyter
.PHONY: pandas
pandas:
	python3 ./scripts/arlington-to-pandas.py --tsvdir ./tsv/latest --save ./scripts/pandas.tsv


# Make the 3D/VR visualization JSON files (all versions)
# Ensure to do a "make tsv" beforehand to refresh the PDF version specific file sets!
.PHONY: 3d
3d:
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/latest --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/2.0 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.7 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.6 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.5 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.4 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.3 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.2 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.1 --outdir ./3dvisualize/
	python3 ./3dvisualize/TSVto3D.py --tsvdir ./tsv/1.0 --outdir ./3dvisualize/


# Validate each of the existing TSV file sets using Python script. Does NOT create the TSVs!
# Ensure to do a "make tsv" beforehand to refresh the PDF version specific file sets!
.PHONY: validate
validate:
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.0/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.1/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.2/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.3/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.4/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.5/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.6/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/1.7/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/2.0/ --validate
	python3 ./scripts/arlington.py --tsvdir ./tsv/latest/ --validate


# Create all TSV file sets for each PDF version based on tsv/latest using Java PoC app. SLOW!
.PHONY: tsv
tsv: ./gcxml/dist/Gcxml.jar
	java -jar ./gcxml/dist/gcxml.jar -tsv


# Make the Java PoC app, run it and then validate the generated XML
xml: ./xml/pdf_grammar1.0.xml ./xml/pdf_grammar1.1.xml ./xml/pdf_grammar1.2.xml ./xml/pdf_grammar1.3.xml \
	./xml/pdf_grammar1.4.xml ./xml/pdf_grammar1.5.xml ./xml/pdf_grammar1.6.xml ./xml/pdf_grammar1.7.xml ./xml/pdf_grammar2.0.xml
	${XMLLINT} ${XMLLINT_FLAGS} --schema ./xml/schema/arlington-pdf.xsd $?

# Create and validate XML files for each PDF version based on tsv/latest using the Java PoC app. SLOW!
xml/%.xml: ./gcxml/dist/Gcxml.jar
	echo "Creating XML: $(strip $(subst xml/pdf_grammar,,$(subst .xml,,$@)))"
	java -jar ./gcxml/dist/gcxml.jar -xml $(strip $(subst xml/pdf_grammar,,$(subst .xml,,$@)))

# Build the Java proof-of-concept application using "ant"
./gcxml/dist/Gcxml.jar:
	( cd gcxml ; ant ; cd .. )