#!/usr/bin/env python3

import sys
import json

def generate_versions(start, end):
    """
    Generates a list of versions between start and end, inclusive.

    Args:
      start: The starting version (e.g., "48").
      end: The ending version (e.g., "434").

    Returns:
      A list of versions as strings.
    """

    # A helper to parse an integer code like 421 => (4, 21)
    def parse_version_code(code: int):
        code_str = str(code)
        major = int(code_str[0])          # e.g. '4'
        minor = int(code_str[1:])         # e.g. '21'
        return major, minor

    major1, minor1 = parse_version_code(start)
    major2, minor2 = parse_version_code(end)

    versions = []
    for minor in range(minor1, minor2 + 1):
        # Reconstruct integer code like (4, 10) => 410
        versions.append(str(major1) + str(minor))

    return versions

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python script.py <start_version> <end_version>")
        sys.exit(1)

    start = sys.argv[1]
    end = sys.argv[2]
    result = generate_versions(start, end)
    print(json.dumps(result))
