///////////////////////////////////////////////////////////////////////////////
// Main.cpp
// Copyright 2020 PDF Association, Inc. https://www.pdfa.org
//
// This material is based upon work supported by the Defense Advanced
// Research Projects Agency (DARPA) under Contract No. HR001119C0079.
// Any opinions, findings and conclusions or recommendations expressed
// in this material are those of the author(s) and do not necessarily
// reflect the views of the Defense Advanced Research Projects Agency
// (DARPA). Approved for public release.
//
// SPDX-License-Identifier: Apache-2.0
// Contributors: Roman Toda, Frantisek Forgac, Normex
///////////////////////////////////////////////////////////////////////////////

#include <exception>
#include <iostream>
#include <string>
#include <filesystem>

#if defined __linux__
#include <cstring>
#endif

#include "Pdfix.h"

#include "ParseObjects.h"
#include "CheckGrammar.h"
#include "TestGrammarVers.h"

void show_help() {
  std::cout << "TestGrammar ver." << TestGrammar_VERSION << std::endl;
  std::cout << "Validates PDF file against Arlington grammar defined by set of TSV files." << std::endl;
  std::cout << std::endl;
  std::cout << "Validate a single PDF file against the Arlington grammar:" << std::endl;
  std::cout << "  testgrammar <input_file> <grammar_folder> <report_file>" << std::endl;
  std::cout << "    input_file      - full pathname to input PDF file " << std::endl;
  std::cout << "    grammar_folder  - folder with TSV files representing Arlington Grammar" << std::endl;
  std::cout << "    report_file     - file for storing results" << std::endl;
  std::cout << std::endl;
  std::cout << "Recursively validate a folder with PDF files against the Arlington grammar:" << std::endl;
  std::cout << "  testgrammar <input_folder> <grammar_folder> <report_folder>" << std::endl;
  std::cout << "    input_folder      - folder with PDF files" << std::endl;
  std::cout << "    grammar_folder    - folder with TSV files representing Arlington Grammar" << std::endl;
  std::cout << "    report_folder     - folder for storing results" << std::endl;
  std::cout << std::endl;
  std::cout << "Check Arlington grammar itself:" << std::endl;
  std::cout << "  testgrammar -v <grammar_folder> <report_file>" << std::endl;
  std::cout << "    grammar_folder  - folder with TSV files representing Arlington Grammar" << std::endl;
  std::cout << "    report_file     - file for storing results" << std::endl;
  std::cout << std::endl;
  std::cout << "Compare Arlington with Adobe's DVA grammar:" << std::endl;
  std::cout << "  testgrammar -c <grammar_folder> <report_file> <dva_grammar_file>" << std::endl;
  std::cout << "    grammar_folder    - folder with TSV files representing Arlington Grammar" << std::endl;
  std::cout << "    report_file       - file for storing results" << std::endl;
  std::cout << "    dva_grammar_file  - PDF file containing Adobe DVA Formal Representation" << std::endl;
}

#ifdef _WIN32
int wmain(int argc, wchar_t* argv[]) {
#else
int main(int argc, char* argv[]) {
#endif
  //clock_t tStart = clock();
  if (argc == 1) {
    show_help();
    return 0;
  }
  std::wstring a1, a2, a3, a4;
  auto i = 1;

#ifdef _WIN32
  // Simplistic attempt at support for folder and filenames from non-std code pages...
  setlocale(LC_CTYPE, "en_US.UTF-8");

  if (argc > i) a1 = argv[i++];
  if (argc > i) a2 = argv[i++];
  if (argc > i) a3 = argv[i++];
  if (argc > i) a4 = argv[i++];
#else //if defined __linux__
  if (argc > i) a1 = utf8ToUtf16(argv[i++]);
  if (argc > i) a2 = utf8ToUtf16(argv[i++]);
  if (argc > i) a3 = utf8ToUtf16(argv[i++]);
  if (argc > i) a4 = utf8ToUtf16(argv[i++]);
#endif

  if (a1 == L"/?") {
    show_help();
    return 0;
  }

  std::string grammar_folder = check_folder_path(ToUtf8(a2));
  std::wstring save_path = a3; //"w:\\report.txt";

  // check grammar itself?
  if (a1 == L"-v") {
    std::ofstream ofs;
    ofs.open(ToUtf8(save_path));
    CheckGrammarFolder(grammar_folder, ofs);
    ofs.close();
    return 0;
  }

  std::wstring input_file = a1;

  // initialize Pdfix
  std::wstring email = L"PDF Assoc. SafeDocs";
  std::wstring license_key = L"jgrrknzeuaDobhTt";

  if (!Pdfix_init(Pdfix_MODULE_NAME))
    throw std::runtime_error("Pdfix: Initialization failed");
  Pdfix* pdfix = GetPdfix();
  if (!pdfix)
    throw std::runtime_error("Pdfix: GetPdfix failed");
  if (pdfix->GetVersionMajor() != PDFIX_VERSION_MAJOR ||
    pdfix->GetVersionMinor() != PDFIX_VERSION_MINOR ||
    pdfix->GetVersionPatch() != PDFIX_VERSION_PATCH)
    throw std::runtime_error("Pdfix: Incompatible version");

  if (!pdfix->GetAccountAuthorization()->Authorize(email.c_str(), license_key.c_str()))
    throw std::runtime_error("Pdfix: Authorization failed");

  if (a1 == L"-c") {
    std::ofstream ofs;
    ofs.open(ToUtf8(save_path));
    CheckDVA(a4, grammar_folder, ofs);
    ofs.close();
  }
  else {
    PdfDoc* doc = nullptr;
    auto single_pdf = [&](const std::wstring& file_name, std::wstring report_file_name) {
      std::wstring open_file = file_name;
      //open report file
      std::ofstream ofs;
      ofs.open(ToUtf8(report_file_name));
      ofs << "BEGIN - TestGrammar v" << TestGrammar_VERSION << " - \"" << ToUtf8(file_name) << "\" - PDFix v"
        << pdfix->GetVersionMajor() << "." << pdfix->GetVersionMinor() << "." << pdfix->GetVersionPatch() << std::endl;

      try {
        doc = pdfix->OpenDoc(open_file.c_str(), L"");
        if (doc != nullptr) {
          PdsDictionary* trailer = (PdsDictionary*)doc->GetTrailerObject();
          //PdsObject* root = doc->GetRootObject();
          if (trailer != nullptr) {
            //grammar parser
            CParsePDF parser(doc, grammar_folder, ofs);
            PdsObject* type_key = trailer->Get(L"Type");
            std::vector<PdsObject*> empty;
            empty.reserve(100);
            //if Type exists we are working with XRefStream
            if (type_key == nullptr)
              parser.add_parse_object(trailer, "FileTrailer", "Trailer", empty);
            else
              parser.add_parse_object(trailer, "XRefStream", "Trailer", empty);
            parser.parse_object();
          }
          else {
            ofs << "Error: failed to acquire Trailer in:" << ToUtf8(file_name) << std::endl;
          }
        }
        else {
          ofs << "Error: Failed to open: \"" << ToUtf8(file_name) << "\" - PDFix GetError(): " << pdfix->GetError() << std::endl;
        }
      }
      catch (std::exception& ex) {
        ofs << "Error: EXCEPTION: " << ex.what() << std::endl;
      }
      // Finally...
      ofs << "END" << std::endl;
      ofs.close();
      if (doc != nullptr) {
        doc->Close();
      }
    };

    //      auto start = std::chrono::system_clock::now();
    if (folder_exists(input_file)) {
      const std::filesystem::path p(input_file);
      const std::filesystem::path outdir(save_path);  // manage any trailing slash or slash as necessary
      for (const auto& entry : std::filesystem::recursive_directory_iterator(p)) {
        if (entry.is_regular_file() && entry.path().extension().wstring() == L".pdf") {
          std::filesystem::path rptfile(outdir);
          rptfile = rptfile / entry.path().stem();
          rptfile.replace_extension(".txt"); // change .pdf to .txt 
          // If rptfile already exists then try a different filename by appending underscores...
          while (std::filesystem::exists(rptfile)) {
            rptfile.replace_filename(rptfile.stem().string() + "_");
            rptfile.replace_extension(".txt");
          }
          std::cout << "Processing \"" << entry.path().string() << "\" to \"" << rptfile.string() << "\"" << std::endl;
          single_pdf(entry.path().wstring(), rptfile.wstring());
        }
      }
    }
    else
      single_pdf(input_file, save_path);

    //auto end = std::chrono::system_clock::now();
    //std::chrono::duration<double> elapsed_seconds = end - start;
    //std::cout <<  "elapsed time: " << elapsed_seconds.count() << "s\n";
  }
  pdfix->Destroy();

  return 0;
}
