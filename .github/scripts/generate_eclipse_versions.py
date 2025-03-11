#!/usr/bin/env python3

import sys
import json

def generate_versions(start, end):
    """
    Generates a list of versions between start and end, inclusive.

    Args:
      start: The starting version (e.g., "4.8").
      end: The ending version (e.g., "4.34").

    Returns:
      A list of versions as strings.
    """

    start_parts = list(map(int, start.split('.')))
    end_parts = list(map(int, end.split('.')))
    current_parts = start_parts[:]  # Create a copy

    versions = []
    while current_parts[0] < end_parts[0] or (current_parts[0] == end_parts[0] and current_parts[1] <= end_parts[1]):
        versions.append(f"{current_parts[0]}.{current_parts[1]}")

        current_parts[1] += 1
    return versions

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python script.py <start_version> <end_version>")
        sys.exit(1)

    start = sys.argv[1]
    end = sys.argv[2]
    result = generate_versions(start, end)
    print(json.dumps(result))
