# -*- coding: utf-8 -*-
import re
import os
import shutil
import json

DOCS_ROOT = 'http://deeplearning4j.org'
SOURCE_ROOT = 'https://github.com/deeplearning4j/deeplearning4j/tree/master/deeplearning4j/' \
              'deeplearning4j-modelimport/src/main/java/org/deeplearning4j/nn/modelimport/keras/'
TEMPLATE_DIR = 'templates'
TARGET_DIR = 'doc_sources'
BASE_PATH = '../'


def class_to_docs_link(module_name, class_name):
    return DOCS_ROOT + module_name.replace('.', '/') + '#' + class_name


def class_to_source_link(module_name, cls_name):
    return '[[source]](' + SOURCE_ROOT + module_name + '/' + cls_name + '.java)'


def to_java(code):
    return '```java\n' + code + '\n```\n'


def process_main_docstring(doc_string):
    lines = doc_string.split('\n')
    doc = [line.replace('*', '').lstrip(' ').rstrip('/') for line in lines[1:-1] if not '@' in line]
    return '\n'.join(doc)


def process_docstring(doc_string):
    lines = doc_string.split('\n')
    doc = [line.replace('*', '').lstrip(' ').replace('@', '- ') for line in lines]
    return '\n'.join(doc)


def render(signature, doc_string):
    name = signature
    subblocks = ['<b<{}</b> \n{}'.format(name, to_java(signature))]
    if doc_string:
        subblocks.append(doc_string + '\n')
    return '\n\n'.join(subblocks)


def get_main_doc_string(class_string, class_name):
    doc_regex = r'\/\*\*\n([\S\s]*?.*)\*\/\n'  # match "/** ... */" at the top
    doc_string = re.search(doc_regex, class_string)
    doc = process_main_docstring(doc_string.group())
    if not doc_string:
        print('Warning, no doc string found for class {}'.format(class_name))
    return doc, class_string[doc_string.end():]


def get_constructor_data(class_string, class_name):
    constructors = []
    if 'public ' + class_name in class_string:
        while 'public ' + class_name in class_string:
            doc_regex = r'\/\*\*\n([\S\s]*?.*)\*\/\n[\S\s]*?(public ' \
                        + class_name + '.[\S\s]*?){'
            result = re.search(doc_regex, class_string)
            doc_string, signature = result.groups()
            doc = process_docstring(doc_string)
            class_string = class_string[result.end():]
            constructors.append((signature, doc))
    return constructors, class_string

def get_public_method_data(class_string):
    method_regex = r'public [static\s]?[a-zA-Z0-9]* ([a-zA-Z0-9]*)\('
    method_strings = re.findall(method_regex, class_string)

    methods = []
    for method in method_strings:
        doc_regex = r'\/\*\*\n([\S\s]*?.*)\*\/\n[\S\s]*?' + \
                    '(public [static\s]?[a-zA-Z0-9]*.[\S\s]*?){'
        result = re.search(doc_regex, class_string)
        doc_string, signature = result.groups()
        doc = process_docstring(doc_string)
        class_string = class_string[result.end():]
        methods.append((signature, doc))
    return methods


def read_page_data(data):
    classes = []
    module = data.get('module', "")
    if module:
        classes = os.listdir(BASE_PATH + module)
    cls = data.get('class', "")
    if cls:
        classes = cls
    page_data = []
    for c in classes:
        class_string = read_file(BASE_PATH + module + '/' + c)
        class_name = c.strip('.java')
        doc_string, class_string = get_main_doc_string(class_string, class_name)
        constructors, class_string = get_constructor_data(class_string, class_name)
        methods = get_public_method_data(class_string)

        page_data.append([module, class_name, doc_string, constructors, methods])

    return page_data


def clean_target():
    if os.path.exists(TARGET_DIR):
        shutil.rmtree(TARGET_DIR)

    for subdir, dirs, fnames in os.walk(TEMPLATE_DIR):
        for fname in fnames:
            new_subdir = subdir.replace(TEMPLATE_DIR, TARGET_DIR)
            if not os.path.exists(new_subdir):
                os.makedirs(new_subdir)
            if fname[-3:] == '.md':
                fpath = os.path.join(subdir, fname)
                new_fpath = fpath.replace(TEMPLATE_DIR, TARGET_DIR)
                shutil.copy(fpath, new_fpath)



def read_file(path):
    with open(path) as f:
        return f.read()


def create_index_page():
    readme = read_file('../README.md')
    index = read_file('templates/index.md')
    index = index.replace('{{autogenerated}}', readme[readme.find('##'):])
    with open(TARGET_DIR + '/index.md', 'w') as f:
        f.write(index)

def write_content(blocks, page_data):
    assert blocks, 'No content for page ' + page_data['page']

    markdown = '\n----\n\n'.join(blocks)
    path = os.path.join(TARGET_DIR, page_data['page'])
    if os.path.exists(path):
        template = read_file(path)
        assert '{{autogenerated}}' in template, \
                'Template found for {} but missing {{autogenerated}} tag.'.format(path)
        markdown = template.replace('{{autogenerated}}', markdown)
    print('Auto-generating docs for {}'.format(path))

    subdir = os.path.dirname(path)
    if not os.path.exists(subdir):
        os.makedirs(subdir)
    with open(path, 'w') as f:
        f.write(markdown)

if __name__ == '__main__':
    clean_target()
    create_index_page()

    with open('pages.json', 'r') as f:
        json_pages = f.read()
    pages = json.loads(json_pages)

    for page_data in pages:
        data = read_page_data(page_data)
        blocks = []
        for module_name, class_name, doc_string, constructors, methods in data:
            subblocks = []
            link = class_to_source_link(module_name, class_name)
            subblocks.append('<span style="float:right;"> {} </span>'.format(link))
            if module_name:
                subblocks.append('## {}\n'.format(class_name))

            if doc_string:
                subblocks.append(doc_string)

            if constructors:
                subblocks.append('\n---')
                subblocks.append('<b>Constructors</b>\n')
                subblocks.append('\n---\n'.join([render(cs, cd) for (cs, cd) in constructors]))

            if methods:
                subblocks.append('\n---')
                subblocks.append('<b>Methods</b>\n')
                subblocks.append('\n---\n'.join([render(ms, md) for (ms, md) in methods]))
            blocks.append('\n'.join(subblocks))

        write_content(blocks, page_data)

